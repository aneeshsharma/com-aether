package aether.security;

import java.io.*;
import java.security.*;
import java.util.Base64;

public class RSAKeyPairGenerator {
    private PrivateKey privateKey;
    private PublicKey publicKey;

    public RSAKeyPairGenerator() throws NoSuchAlgorithmException {
        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(1024);
        KeyPair key = keygen.generateKeyPair();
        privateKey = key.getPrivate();
        publicKey = key.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getPublicKeyAsString() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public String getPrivateKeyAsString() {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }
}
