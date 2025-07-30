import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import javax.crypto.Cipher;

public class RSATestMain {

    public static void main(String[] args) throws Exception {
        // Sample Base64-encoded RSA keys (replace with your actual keys)
        String base64PublicKey = "<YOUR_BASE64_ENCODED_PUBLIC_KEY>";
        String base64PrivateKey = "<YOUR_BASE64_ENCODED_PRIVATE_KEY>";

        String originalData = "Hello, Ganesh!";

        // Encrypt with public key
        PublicKey publicKey = getPublicKey(base64PublicKey);
        String encrypted = encrypt(originalData, publicKey);
        System.out.println("Encrypted: " + encrypted);

        // Decrypt with private key
        PrivateKey privateKey = getPrivateKey(base64PrivateKey);
        String decrypted = decrypt(encrypted, privateKey);
        System.out.println("Decrypted: " + decrypted);
    }

    public static PublicKey getPublicKey(String base64PublicKey) throws Exception {
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    public static PrivateKey getPrivateKey(String base64PrivateKey) throws Exception {
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64PrivateKey));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    public static String encrypt(String data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public static String decrypt(String encryptedData, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decryptedBytes);
    }
}
