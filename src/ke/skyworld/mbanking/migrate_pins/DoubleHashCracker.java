package ke.skyworld.mbanking.migrate_pins;

import ke.skyworld.lib.mbanking.utils.Crypto;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DoubleHashCracker {

    // Adjust based on the number of CPU cores you want to utilize
    private static final int THREAD_COUNT = 4;

    private static Crypto crypto = new Crypto();

    public static String run(String doubleHashedPIN) {

        final String targetHash = doubleHashedPIN.toLowerCase().trim();
        final AtomicBoolean found = new AtomicBoolean(false);
        final AtomicReference<String> foundPin = new AtomicReference<>(null);

        // Thread pool
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // We have 10,000 possible PINs: 0000 .. 9999
        final int totalPins = 10_000;
        // Divide the workload among THREAD_COUNT threads
        final int chunkSize = (totalPins + THREAD_COUNT - 1) / THREAD_COUNT; 
        // (the +THREAD_COUNT-1 ensures we handle the remainder if it’s not perfectly divisible)

        for (int start = 0; start < totalPins; start += chunkSize) {
            final int rangeStart = start;
            final int rangeEnd = Math.min(start + chunkSize, totalPins);

            executor.submit(() -> {
                for (int i = rangeStart; i < rangeEnd && !found.get(); i++) {
                    // Generate a 4-digit pin with leading zeros (e.g. 0007, 0123, etc.)
                    String pin = String.format("%04d", i);
                    //System.out.println(pin);

                    // Perform double hashing: first MD5, then SHA-512
                    String doubleHashed = doubleHash(pin);

                    // Check if the double-hashed result matches the target
                    if (doubleHashed.equals(targetHash)) {
                        found.set(true);
                        foundPin.set(pin);
                        return; // stop searching in this thread
                    }
                }
            });
        }

        // Shut down the thread pool
        executor.shutdown();
        // Wait for all tasks to complete
        while (!executor.isTerminated()) {
            // Optional small sleep to avoid busy-wait if desired
            // Thread.sleep(50);
        }

        if (found.get()) {
            //System.out.println("Match found! PIN: " + foundPin.get());
            return foundPin.get();
        } else {
            //System.out.println("No match found in range 0000–9999.");
            return null;
        }
    }

    /**
     * Performs MD5 on the input, then SHA-512 on the MD5 digest.
     * @param input the 4-digit PIN to hash
     * @return the hex representation of SHA-512(MD5(input))
     */
    private static String doubleHash(String input) {

        return crypto.hash("SHA-512", crypto.hash("MD5", input));

        /*try {
            // 1) MD5
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] md5Digest = md5.digest(input.getBytes());

            // 2) SHA-512 of the MD5 hash bytes
            MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
            byte[] shaDigest = sha512.digest(md5Digest);

            return bytesToHex(shaDigest);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Required algorithm not found", e);
        }*/
    }

    /**
     * Converts a byte array to hexadecimal representation.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
