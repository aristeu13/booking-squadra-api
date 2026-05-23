package com.bookingsquadra.config;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(EfiPayProperties.class)
public class EfiPayConfig {

    private static final Logger log = LoggerFactory.getLogger(EfiPayConfig.class);

    private static final String DEFAULT_BASE_URL = "https://pix-h.api.efipay.com.br";

    /**
     * RestClient for EfiPay's PIX API. Uses mTLS via an in-memory keystore built from PEM
     * strings supplied through configuration (intended to be sourced from environment vars).
     * No default {@code Authorization} header — callers attach Basic auth on the token
     * endpoint and Bearer auth on subsequent calls.
     */
    @Bean
    RestClient efiPayRestClient(EfiPayProperties props) {
        String baseUrl = props.baseUrl() != null && !props.baseUrl().isBlank()
                ? props.baseUrl()
                : DEFAULT_BASE_URL;

        HttpClient.Builder httpBuilder = HttpClient.newBuilder();
        if (props.isMtlsConfigured()) {
            httpBuilder.sslContext(buildSslContext(props.certificatePem(), props.privateKeyPem()));
        } else {
            log.warn("EfiPay mTLS certificate/key are not set — EfiPay calls will fail until configured");
        }

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpBuilder.build());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private static SSLContext buildSslContext(String certificatePem, String privateKeyPem) {
        try {
            List<X509Certificate> chain = parseCertificateChain(certificatePem);
            if (chain.isEmpty()) {
                throw new IllegalStateException("EfiPay certificate PEM contained no certificates");
            }
            PrivateKey privateKey = parsePrivateKey(privateKeyPem);

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            char[] password = new char[0];
            keyStore.setKeyEntry("efipay", privateKey, password, chain.toArray(new Certificate[0]));

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
            return sslContext;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build EfiPay SSL context", e);
        }
    }

    private static List<X509Certificate> parseCertificateChain(String pem) throws Exception {
        String normalized = normalizeNewlines(pem);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certs = new ArrayList<>();
        try (ByteArrayInputStream in = new ByteArrayInputStream(normalized.getBytes(StandardCharsets.UTF_8))) {
            for (Certificate c : cf.generateCertificates(in)) {
                certs.add((X509Certificate) c);
            }
        }
        return certs;
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        String body = normalizeNewlines(pem)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        if (body.isEmpty()) {
            throw new IllegalStateException("EfiPay private key PEM is empty (expected PKCS#8 'BEGIN PRIVATE KEY')");
        }
        byte[] der = Base64.getDecoder().decode(body);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (InvalidKeySpecException rsaFailure) {
            try {
                return KeyFactory.getInstance("EC").generatePrivate(spec);
            } catch (InvalidKeySpecException ecFailure) {
                IllegalStateException ex = new IllegalStateException(
                        "EfiPay private key is not a supported PKCS#8 RSA or EC key");
                ex.addSuppressed(rsaFailure);
                ex.addSuppressed(ecFailure);
                throw ex;
            }
        }
    }

    private static String normalizeNewlines(String s) {
        return s.replace("\r\n", "\n");
    }
}
