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
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;

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
        if (privateFile.exists()) {
            byte[] encoded = readAll(privateFile);
            PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
            // We store public values in a sidecar for simplicity; if missing, recreate the pair.
            File publicFile = new File(privateFile.getParentFile(), privateFile.getName() + ".pub.der");
            if (publicFile.exists()) {
                java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(readAll(publicFile));
                RSAPublicKey pub = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
                return new AdbKey(pk, pub);
            }
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        privateFile.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(privateFile)) {
            fos.write(pair.getPrivate().getEncoded());
        }
        File publicFile = new File(privateFile.getParentFile(), privateFile.getName() + ".pub.der");
        try (FileOutputStream fos = new FileOutputStream(publicFile)) {
            fos.write(pair.getPublic().getEncoded());
        }
        return new AdbKey(pair.getPrivate(), (RSAPublicKey) pair.getPublic());
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
}
