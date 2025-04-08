package com.eiyooooo.adblink.adb;

import android.sun.misc.BASE64Encoder;
import android.sun.security.provider.X509Factory;
import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

import androidx.annotation.NonNull;

import com.eiyooooo.adblink.MyApplicationKt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import timber.log.Timber;

class AdbKeyPair {
    private final PrivateKey privateKey;
    private final Certificate certificate;
    private final String keyName;

    private static AdbKeyPair INSTANCE;

    public static AdbKeyPair getInstance() {
        if (INSTANCE == null) {
            INSTANCE = loadKeyPair();
            if (INSTANCE == null) {
                try {
                    INSTANCE = createAdbKeyPair();
                } catch (Exception e) {
                    Timber.w(e, "Failed to create ADB key pair");
                }
            }
        }
        return INSTANCE;
    }

    private AdbKeyPair(PrivateKey privateKey, Certificate certificate, String keyName) {
        this.privateKey = privateKey;
        this.certificate = certificate;
        this.keyName = keyName;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public PublicKey getPublicKey() {
        return certificate.getPublicKey();
    }

    public String getKeyName() {
        return keyName;
    }

    private static AdbKeyPair loadKeyPair() {
        PrivateKey mPrivateKey = null;
        try {
            File privateKeyFile = new File(MyApplicationKt.getApplication().getFilesDir(), "private.key");
            if (privateKeyFile.exists()) {
                byte[] privateKeyBytes = new byte[(int) privateKeyFile.length()];
                try (FileInputStream is = new FileInputStream(privateKeyFile)) {
                    int ignored = is.read(privateKeyBytes);
                }
                EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
                mPrivateKey = KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec);
            }
        } catch (Exception e) {
            Timber.w(e, "Failed to read private key");
        }

        Certificate mCertificate = null;
        try {
            File certFile = new File(MyApplicationKt.getApplication().getFilesDir(), "cert.pem");
            if (certFile.exists()) {
                try (FileInputStream cert = new FileInputStream(certFile)) {
                    mCertificate = CertificateFactory.getInstance("X.509").generateCertificate(cert);
                }
            }
        } catch (Exception e) {
            Timber.w(e, "Failed to read certificate");
        }

        if (mPrivateKey != null && mCertificate != null) {
            return new AdbKeyPair(mPrivateKey, mCertificate, getKeyName(mPrivateKey));
        } else {
            return null;
        }
    }

    private static AdbKeyPair createAdbKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
        java.security.KeyPair generateKeyPair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = generateKeyPair.getPublic();
        PrivateKey privateKey = generateKeyPair.getPrivate();

        String keyName = getKeyName(privateKey);
        String subject = "CN=" + keyName;
        String algorithmName = "SHA512withRSA";
        long expiryDate = System.currentTimeMillis() + 86400000;
        CertificateExtensions certificateExtensions = new CertificateExtensions();
        certificateExtensions.set(
                "SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(
                        new KeyIdentifier(publicKey).getIdentifier()
                )
        );
        X500Name x500Name = new X500Name(subject);
        Date notBefore = new Date();
        Date notAfter = new Date(expiryDate);
        certificateExtensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));
        CertificateValidity certificateValidity = new CertificateValidity(notBefore, notAfter);
        X509CertInfo x509CertInfo = new X509CertInfo();
        x509CertInfo.set("version", new CertificateVersion(2));
        x509CertInfo.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
        x509CertInfo.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithmName)));
        x509CertInfo.set("subject", new CertificateSubjectName(x500Name));
        x509CertInfo.set("key", new CertificateX509Key(publicKey));
        x509CertInfo.set("validity", certificateValidity);
        x509CertInfo.set("issuer", new CertificateIssuerName(x500Name));
        x509CertInfo.set("extensions", certificateExtensions);
        X509CertImpl x509CertImpl = new X509CertImpl(x509CertInfo);
        x509CertImpl.sign(privateKey, algorithmName);

        File privateKeyFile = new File(MyApplicationKt.getApplication().getFilesDir(), "private.key");
        try (FileOutputStream os = new FileOutputStream(privateKeyFile)) {
            os.write(privateKey.getEncoded());
        }
        File certFile = new File(MyApplicationKt.getApplication().getFilesDir(), "cert.pem");
        BASE64Encoder encoder = new BASE64Encoder();
        try (FileOutputStream os = new FileOutputStream(certFile)) {
            os.write(X509Factory.BEGIN_CERT.getBytes(StandardCharsets.UTF_8));
            os.write('\n');
            encoder.encode(x509CertImpl.getEncoded(), os);
            os.write('\n');
            os.write(X509Factory.END_CERT.getBytes(StandardCharsets.UTF_8));
        }

        return new AdbKeyPair(privateKey, x509CertImpl, keyName);
    }

    public static void recreateAdbKeyPair() {
        try {
            INSTANCE.getPrivateKey().destroy();
        } catch (Exception e) {
            Timber.w(e, "Failed to destroy private key");
        }
        File privateKeyFile = new File(MyApplicationKt.getApplication().getFilesDir(), "private.key");
        if (privateKeyFile.exists()) {
            boolean ignored = privateKeyFile.delete();
        }
        File certFile = new File(MyApplicationKt.getApplication().getFilesDir(), "cert.pem");
        if (certFile.exists()) {
            boolean ignored = certFile.delete();
        }
        try {
            INSTANCE = createAdbKeyPair();
        } catch (Exception e) {
            Timber.w(e, "Failed to create ADB key pair");
        }
    }

    private static String getKeyName(@NonNull PrivateKey privateKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
            int hashCode = keyFactory.generatePrivate(privateKeySpec).hashCode();
            String hash = hashCode >= 0 ? String.valueOf(hashCode) : String.valueOf(-hashCode);
            if (hash.length() <= 4) return "ADBLink-" + hash;
            else return "ADBLink-" + hash.substring(hash.length() - 4);
        } catch (Exception e) {
            Timber.w(e, "Failed to get key name");
            return "ADBLink";
        }
    }
}
