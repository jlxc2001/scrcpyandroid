package com.jlxc.scrcpyclient.adb;

import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;

public final class AdbKey {
    private static final int MODULUS_SIZE_WORDS = 64; // 2048 bit / 32
    private static final int MODULUS_SIZE_BYTES = MODULUS_SIZE_WORDS * 4;

    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;

    private AdbKey(PrivateKey privateKey, RSAPublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public static AdbKey loadOrCreate(File privateFile) throws Exception {
        File dir = privateFile.getParentFile();
        if (dir != null) dir.mkdirs();
        File publicFile = new File(privateFile.getParentFile(), privateFile.getName() + ".pub.der");

        if (privateFile.exists()) {
            try {
                byte[] encoded = readAll(privateFile);
                PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
                RSAPublicKey pub;
                if (publicFile.exists()) {
                    java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(readAll(publicFile));
                    pub = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
                } else if (pk instanceof RSAPrivateCrtKey) {
                    RSAPrivateCrtKey crt = (RSAPrivateCrtKey) pk;
                    RSAPublicKeySpec spec = new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent());
                    pub = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
                    writeAll(publicFile, pub.getEncoded());
                } else {
                    throw new IllegalStateException("private key exists but public part cannot be reconstructed");
                }
                return new AdbKey(pk, pub);
            } catch (Exception e) {
                // Keep the bad file for debugging, then create a fresh persistent key.
                File bad = new File(privateFile.getParentFile(), privateFile.getName() + ".bad." + System.currentTimeMillis());
                //noinspection ResultOfMethodCallIgnored
                privateFile.renameTo(bad);
                //noinspection ResultOfMethodCallIgnored
                publicFile.delete();
            }
        }

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        writeAll(privateFile, pair.getPrivate().getEncoded());
        writeAll(publicFile, pair.getPublic().getEncoded());
        return new AdbKey(pair.getPrivate(), (RSAPublicKey) pair.getPublic());
    }

    public String fingerprint() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(adbPublicKey(publicKey));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < digest.length; i++) {
                if (i > 0) sb.append(':');
                String h = Integer.toHexString(digest[i] & 0xff);
                if (h.length() == 1) sb.append('0');
                sb.append(h);
            }
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public byte[] sign(byte[] token) throws Exception {
        Signature sig = Signature.getInstance("SHA1withRSA");
        sig.initSign(privateKey);
        sig.update(token);
        return sig.sign();
    }

    public byte[] publicKeyPayload(String comment) {
        byte[] adbKey = adbPublicKey(publicKey);
        String b64 = Base64.encodeToString(adbKey, Base64.NO_WRAP);
        return (b64 + " " + comment + "\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // Android ADB public key format: base64(RSAPublicKey struct) + comment.
    private static byte[] adbPublicKey(RSAPublicKey key) {
        BigInteger n = key.getModulus();
        BigInteger e = key.getPublicExponent();
        BigInteger two32 = BigInteger.ONE.shiftLeft(32);
        long n0 = n.mod(two32).longValue() & 0xffffffffL;
        BigInteger n0Bi = BigInteger.valueOf(n0);
        long inv = two32.subtract(n0Bi.modInverse(two32)).longValue() & 0xffffffffL;
        BigInteger r = BigInteger.ONE.shiftLeft(MODULUS_SIZE_WORDS * 32);
        BigInteger rr = r.multiply(r).mod(n);

        ByteBuffer out = ByteBuffer.allocate(4 + 4 + MODULUS_SIZE_BYTES + MODULUS_SIZE_BYTES + 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(MODULUS_SIZE_WORDS);
        out.putInt((int) inv);
        out.put(toFixedLittleEndian(n, MODULUS_SIZE_BYTES));
        out.put(toFixedLittleEndian(rr, MODULUS_SIZE_BYTES));
        out.putInt(e.intValue());
        return out.array();
    }

    private static byte[] toFixedLittleEndian(BigInteger value, int size) {
        byte[] be = value.toByteArray();
        byte[] le = new byte[size];
        int src = be.length - 1;
        int dst = 0;
        while (src >= 0 && dst < size) le[dst++] = be[src--];
        return le;
    }

    private static byte[] readAll(File f) throws Exception {
        byte[] data = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int off = 0;
            while (off < data.length) {
                int r = fis.read(data, off, data.length - off);
                if (r < 0) break;
                off += r;
            }
        }
        return data;
    }

    private static void writeAll(File f, byte[] data) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data);
            fos.flush();
            fos.getFD().sync();
        }
    }
}
