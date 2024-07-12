package bungalowserver;

import bungalowserver.Log.Level;
import java.io.Console;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

/**
 * Crypto class for encrypting and decrypting.
 * @author SJOHNS
 */
public class Crypto {

    private static SecretKey secretKey = null;

    //private static final String blockMode = "AES/CBC/PKCS7Padding";
    private static final String BLOCK_MODE = "AES/CBC/PKCS5Padding";
    private static final String PBE_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final String HASH_ALGORITHM = "SHA-256";

    // Note on hash iterations: larger is more secure but takes longer
    // to compute (heavier cpu load), use 256 or greater
    private static final int HASH_ITERATIONS = 256;

    // Encryption mode
    public enum Mode { AES128, AES256 }

    /**
     * Generates a secure encryption key from a password and salt.
     * @param  password The password used to generate the secrete key.
     * @param  salt An 8 byte salt value (pass null if no salt)
     * @param  mode Security level (Mode.AES128 or Mode.AES256)
    */
    public static void generateSecureKey(char[] password, byte[] salt, Mode mode) {
        Log.print(Level.INFO, "Generating encryption key...");

        // Check for null or empty password
        if (password == null || password.length == 0) {
            Log.print(Level.ERROR, "Crypto error, password is null!");
            throw new IllegalArgumentException("Crypto error, password is null!");
        }

        // Check salt (required with length 8)
        if ((salt == null) || (salt.length != 8)) {
            Log.print(Level.ERROR, "Crypto error, bad salt length!");
            throw new IllegalArgumentException("Crypto error, bad salt length!");
        }

        // Add the salt and perform the hash using PBEKeySpec
        PBEKeySpec pbeKeySpec = new PBEKeySpec(password,
                salt, HASH_ITERATIONS, (mode == Mode.AES128) ? 128 : 256);

        // Done with password and salt so clear them from memory
        Arrays.fill(password, (char) 0);
        Arrays.fill(salt, (byte) 0);

        // Generate encryption key
        try {
            secretKey = new SecretKeySpec(
                    SecretKeyFactory.getInstance(PBE_ALGORITHM)
                            .generateSecret(pbeKeySpec).getEncoded(), "AES");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            Log.print(Level.ERROR, "Unable to generate encryption key! " + ex.getMessage());
            throw new RuntimeException("Unable to generate encryption key!" + ex.getMessage(), ex);
        }
    }

    /**
     * This method uses Strings for password and salt, this is not recommended.
     * You should consider the array methods below for better security.
     * @param password password string
     * @param salt hex string (16 hex chars representing 8 bytes)
     * @param mode encryption mode
     */
    public static void generateKey(String password, String salt, Mode mode) {

        // Convert salt hex string to a byte array and catch non-hex characters
        byte[] mySalt;
        try {
            mySalt = DatatypeConverter.parseHexBinary(salt);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Crypto error, salt contains non-hex characters!", ex);
        }

        generateSecureKey(password.toCharArray(), mySalt, mode);
    }

    /**
     * Generates the encryption key from the password using a hash and AES
     * encryption. Should favor generateSecureKey method instead since that
     * method uses PBKDF2WithHmacSHA1 and is more secure.
     * @param  password The password used to generate the secrete key.
     * @param  salt An 8 byte salt value (pass null if no salt)
     * @param  mode Security level (Mode.AES128 or Mode.AES256)
    */
    public static void generateHashKey(char[] password, byte[] salt, Mode mode) {
        Log.print(Level.INFO, "Hashing...");

        // Check for null or empty password
        if (password == null || password.length == 0) {
            Log.print(Level.ERROR, "Crypto error, password is null!");
            throw new IllegalArgumentException("Crypto error, password is null!");
        }

        // Check salt (allow null or length 8)
        if ((salt != null) && (salt.length != 8)) {
            Log.print(Level.ERROR, "Crypto error, bad salt length!");
            throw new IllegalArgumentException("Crypto error, bad salt length!");
        }

        // Get a message digest that implements the specified algorithm
        // And update the digest using the password encoded as UTF8
        byte[] mdkey;
        try {
            //mdkey = MessageDigest.getInstance(HASH_ALGORITHM).digest(charsToBytes(password));
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            if (salt != null) {
                md.update(salt);
            }
            md.update(charsToBytes(password));
            mdkey = md.digest();
        } catch (NoSuchAlgorithmException ex) {
            Log.print(Level.ERROR, "Encryption algorithm error! " + ex.getMessage());
            throw new RuntimeException("Encryption algorithm error! " + ex.getMessage(), ex);
        }

        // Done with password and salt so clear them from memory
        Arrays.fill(password, (char) 0);
        if (salt != null) {
            Arrays.fill(salt, (byte) 0);
        }

        // Shorten the key if using AES128 (instead of AES256)
        if (mode == Mode.AES128) {
            mdkey = Arrays.copyOf(mdkey, 16); // if aes128 then just use the first 128 bits
        }

        // Construct the secret key using the specified algorithm
        secretKey = new SecretKeySpec(mdkey, "AES");
    }

    /**
     * Encrypts a string with AES encryption and converts to base-64 for easy
     * transport over TCP.
     * @param  clearText The string to encrypt.
     * @return the encrypted string
    */
    public static String encrypt(String clearText) {
        if (clearText == null) return null;
        try {
            return encrypt(clearText.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            Log.print(Level.ERROR, "Unable to encrypt message! " + ex.getMessage());
            throw new RuntimeException("Unable to encrypt message!" + ex.getMessage(), ex);
        }
    }

    /**
     * Encrypts a byte array.
     * @param  clearText The byte array to encrypt.
     * @return the encrypted string or null if byte array is null or empty
    */
    public static String encrypt(byte[] clearText) {

        if (clearText == null || clearText.length == 0) {
            return null;
        }

        // Check the secret key
        if (secretKey == null) {
            Log.print(Level.ERROR, "Crypto not initialized (must call generateKey)!");
            throw new RuntimeException("Crypto not initialized (must call generateKey)!");
        }

        Cipher cipher;
        byte[] cipherData;

        try {
            // Get a cipher that implements the specified BLOCK_MODE ("AES/CBC/PKCS5Padding");
            cipher = Cipher.getInstance(BLOCK_MODE);
            // Initialize the cipher with the secret key, let it generate a random iv
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            // Encrypt the message
            cipherData = cipher.doFinal(clearText);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException ex) {
            Log.print(Level.ERROR, "Cipher error! " + ex.getMessage());
            throw new RuntimeException("Cipher error!" + ex.getMessage(), ex);
        } catch (IllegalBlockSizeException | BadPaddingException ex) {
            // If client uses bad password then fails here for BadPaddingException
            Log.print(Level.WARN, "Unable to encrypt message! " + ex.getMessage());
            throw new RuntimeException("Unable to encrypt message!" + ex.getMessage(), ex);
        }

        // Get the new random iv to send to the receiver
        byte[] initVector = cipher.getIV();

        // Prepend the iv to the encrypted data
        byte[] fullPacket = new byte[initVector.length + cipherData.length];
        System.arraycopy(initVector, 0, fullPacket, 0, initVector.length);
        System.arraycopy(cipherData, 0, fullPacket, initVector.length, cipherData.length);

        // Encode base64
        return Base64.getEncoder().encodeToString(fullPacket);
    }

    /**
     * Decrypts a string.
     * @param  encryptedText The string to decrypt.
     * @return the encrypted string. Returns null if unable to decrypt
     * (this will occur if client sends invalid).
    */
    public static String decrypt(String encryptedText) {
        byte[] ba = decryptToByteArray(encryptedText);
        if (ba == null) return null;
        return new String(ba, StandardCharsets.UTF_8);
    }

    /**
     * Decrypts a string to a byte array.
     * @param  encryptedText The string to decrypt.
     * @return the decrypted data as a byte array.
     * Returns an empty array if the parameter is null or empty string.
     * Returns null if the client sent an invalid message (unable to decrypt)
    */
    public static byte[] decryptToByteArray(String encryptedText) {

        if (encryptedText == null || encryptedText.isEmpty()) {
            return new byte[0];
        }

        // Check the secret key
        if (secretKey == null) {
            Log.print(Level.ERROR, "Crypto not initialized (must call generateKey)!");
            throw new RuntimeException("Crypto not initialized (must call generateKey)!");
        }

        // Decode base64
        byte[] cipherText;
        try {
            cipherText = Base64.getDecoder().decode(encryptedText);
        } catch (IllegalArgumentException ex) {
            Log.print(Level.WARN, "Unable to decrypt message, not base64! " + ex.getMessage());
            return null;  // null indicates client sent invalid message
        }

        // Split out the iv (without encrypted data)
        byte[] initVector = new byte[16];
        System.arraycopy(cipherText, 0, initVector, 0, 16);

        // Split out the encrypted data (without iv)
        byte[] cipherData = new byte[cipherText.length - 16];
        System.arraycopy(cipherText, 16, cipherData, 0, cipherText.length - 16);

        Cipher cipher;
        try {
            // Get a cipher that implements the specified BLOCK_MODE ("AES/CBC/PKCS5Padding");
            cipher = Cipher.getInstance(BLOCK_MODE);
            // Initialize the cipher with the secret key and a algorithm
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(initVector));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException |
                InvalidKeyException | InvalidAlgorithmParameterException ex) {
            Log.print(Level.ERROR, "Cipher error! " + ex.getMessage());
            throw new RuntimeException("Cipher error!" + ex.getMessage(), ex);
        }

        // Decrypt the message
        try {
            return cipher.doFinal(cipherData);
        } catch (IllegalBlockSizeException | BadPaddingException ex) {
            // Note: bad password causes BadPaddingException exception here
            Log.print(Level.INFO, "Unable to decrypt message (possible bad pw from client)!");
            return null;  // null indicates client sent invalid message
        }
    }

    /**
     * Obfuscate
     * @param clrText clear text
     * @return Obfuscated text as a byte array
     */
    public static byte[] obfuscate(char[] clrText) {
        int ofs = 0;
        byte[] ba = new byte[clrText.length * 4 + 2];
        for (int i = 0; i < clrText.length; i++) {
            byte a = (byte)(clrText[clrText.length - (i + 1)]);
            int n = ((127 + clrText[i] + a) << 8) + (127 + clrText[i] - a);
            ba[ofs++] = intToByte(n >> 16);
            ba[ofs++] = intToByte(n >> 8);
            ba[ofs++] = intToByte((n & 0xff) / 7);
            ba[ofs++] = intToByte((n & 0xff) % 7);
        }

        byte pre = ba[0];
        for (int i = 1; i < (ba.length - 2); i++) {
            ba[i] += (pre % 21);
        }

        int cs = 0;
        for (int i = 0; i < (ba.length - 2); i++) {
            cs += byteToInt(ba[i]);
        }
        cs = ~cs + 1;
        ba[ba.length-2] = intToByte(cs >> 8);
        ba[ba.length-1] = intToByte(cs);
        return Base64.getEncoder().encode(ba);
    }

    /**
     * Deobfuscate
     * @param obfText
     * @return char array
     */
    public static char[] deobfuscate(byte[] obfText) {
        byte[] ba = Base64.getDecoder().decode(obfText);

        int cs = 0;
        for (int i = 0; i < (ba.length - 2); i++) {
            cs += byteToInt(ba[i]);
        }
        cs += byteToInt(ba[ba.length - 2]) << 8;
        cs += byteToInt(ba[ba.length - 1]);
        cs &= 0xffff;
        if (cs != 0) {
            // checksum error
            return null;
        }

        byte pre = ba[0];
        for (int i = 1; i < (ba.length - 2); i++) {
            ba[i] -= (pre % 21);
        }
        char[] ch = new char[(ba.length - 2) / 4];
        int ofs = 0;
        for (int i = 0; i < (ba.length - 2); i += 4) {
            int a = (int)(((ba[i] & 0xff) << 8) + (ba[i+1] & 0xff) + (ba[i+2] & 0xff) * 7 + (ba[i+3] & 0xff));
            ch[ofs++] = (char) ((a - 254) / 2);
        }
        return ch;
    }

    private static int byteToInt(byte b) {
        return (int)(b & 0xff);
    }

    private static byte intToByte(int i) {
        return (byte)(i & 0xff);
    }

    /**
     * Converts a char array to a byte array
     * @param chars char array
     * @return byte array
     */
    private static byte[] charsToBytes(char[] chars) {
        byte[] bytes = new byte[chars.length];
        for (int i = 0; i < chars.length; i++) {
            bytes[i] = (byte)(chars[i]);
            chars[i] = (char)0;  // clear sensitive data
        }
        return bytes;
    }

    /**
     * Reads obfuscated text from file, deobfuscates and returns it as char array
     * @param filepath
     * @return deobfuscated text or null if file doesn't exist
     */
    public static char[] deobfuscateFromFile(String filepath) {
        char[] pw = null;
        try {
            pw = deobfuscate(Files.readAllBytes(Paths.get(filepath)));
        } catch (IOException ex) {
        }
        return pw;
    }

    /**
     * Obfuscates text and writes it to a file
     * @param filepath
     * @param text
     * @return true on success else false
     */
    public static boolean obfuscateToFile(String filepath, char[] text) {
        try {
            Files.write(Paths.get(filepath), obfuscate(text));
        } catch (IOException ex) {
            return false;
        }
        return true;
    }

    /**
     * Prompts user for a password and returns the password as a
     * char array (for security... can be cleared)
     * @return
     */
    public static char[] promptForPassword() {

        // Prompt for pw
        Console console = System.console();
        if (console == null) {
            System.out.println("Couldn't get Console!");
            System.exit(0);
        }

        char[] pwd;

        while (true) {

            pwd = console.readPassword("\nEnter server password: ");
            char[] pwdv = console.readPassword("Re-enter server password (for verification): ");

            if (Arrays.equals(pwd, pwdv) == false) {
                System.out.println("Passwords did not match, try again");
                continue;
            }

            Arrays.fill(pwdv, 'x');

            if (pwd.length < 6) {
                System.out.println("Passwords is too short, try again");
                continue;
            }
            break;
        }
        return pwd;
    }

    /*private int getRandom() {
        SecureRandom r;
        try {
            r = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Crypto error, no such algorithm! " + ex.getMessage());
        }
    }*/
}



/* Notes:

A new initialization vector (iv) is required for each new encrypted packet and
this iv must be sent along (unencrypted) with the encrypted data for every
packet by prepending it to the encrypted data (prior to encoding to Base64).
The receiver then uses this random data, along with the private password/key
to decrypt the data. The iv is random data that must be known by both sides and
must not be encrypted. It poses no security threat.
The IV is always 16 bytes (128 bits) for all AES modes regardless if using
AES128/192/256.

Salt notes:
Salt is a random 8 bytes that must be known to the server and the clients. It
is used along with the password to ensure the generated private key is secure
and not susceptible to dictionary attacks. The private key is generated from the
combination of the password and the salt. The salt can be hard-coded and the
same salt should then hard-coded in the server code.
Note: using a printable string for salt (instead of hex chars) reduces the
number of possibilities for bytes since it is only printable chars, so I use a
hex string which is converted to a byte-array to allow reprenting all values.
Both the server and the client must use the same password and salt to end up
with the same key. Salt should be 8 bytes in length. The generated key length
is the same length as aesBits (128 or 256) since key length determines the
aes mode (128 or 256)
*/

// Note:
// If not using encryption then no need for this class, just call this to convert to base64
// import android.util.Base64;
// Encrypt:
// Base64.encodeToString(mystring.getBytes("UTF-8"), Base64.NO_WRAP);  // if string or can send without base64
// Base64.encodeToString(mybytearray, Base64.NO_WRAP);  // if byte array (must use base64 to convert binary data to a string)
// Decrypt:
// byte[] cipherText = Base64.decode(encryptedText, Base64.NO_WRAP);