package ke.skyworld.mbanking.migrate_pins;

import de.siegmar.fastcsv.reader.CsvContainer;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.enums.ReturnValue;
import ke.co.skyworld.smp.utility_items.logging.LoggerConfiguration;
import ke.co.skyworld.smp.utility_items.memory.JvmManager;
import ke.co.skyworld.smp.utility_items.security.CryptoInit;
import ke.skyworld.mbanking.cbs.ApStarCBSParams;
import ke.skyworld.mbanking.cbs.CBSAPI;
import org.apache.log4j.BasicConfigurator;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * apstar_mbanking (ke.skyworld.mbanking.migrate_pins)
 * Created by: dmutende
 * On: 28 Jan, 2025 21:29
 **/
public class MigratePINs {

    private static final String OPERATION_MODE = "MIGRATE_DISABLE_KYC"; //MIGRATE/MIGRATE_DISABLE_KYC/FORCE_KYC/DISABLE_KYC
    private static final int THREAD_COUNT = 20; // Number of threads to use

    public static void run() throws Exception {
        CryptoInit.init();

        LoggerConfiguration.initialize();
        BasicConfigurator.configure();

        if (Repository.setup() == ReturnValue.ERROR) {
            System.exit(-1);
        }

        ApStarCBSParams.initialize();

        System.out.println("Migrating PINs...");
        System.out.println();

        // Read CSV
        File file = null;
        CsvReader csvReader = null;

        try {
            file = new File("ukl_mbanking_members_full.csv");
            csvReader = new CsvReader();
            csvReader.setContainsHeader(true);
            csvReader.setFieldSeparator(',');
            csvReader.setTextDelimiter('"');

            CsvContainer csvContainer = csvReader.read(file, StandardCharsets.UTF_8);
            long recordCount = csvContainer.getRowCount();
            System.out.println("Processing " + recordCount + " member(s)...");
            System.out.println();

            int threadCount = THREAD_COUNT; // Number of threads
            int batchSize = (int) Math.ceil((double) recordCount / threadCount);

            int threadId = 0;

            // Thread pool
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                threadId++;
                int start = i * batchSize;
                int end = Math.min(start + batchSize, (int) recordCount);

                List<CsvRow> batch = csvContainer.getRows().subList(start, end);

                // Submit batch processing as a task
                int finalThreadId = threadId;
                executor.submit(() -> processBatch(batch, finalThreadId));
                Thread.sleep(1000);
            }

            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.HOURS); // Wait for all threads to complete

            System.out.println("Job Complete!");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JvmManager.gc(csvReader, file);
        }
    }

    private static void processBatch(List<CsvRow> rows, int threadId) {

        long counter = 0;
        long totalThreadRecords = rows.size();

        for (CsvRow row : rows) {
            counter++;

            String mobileNumber = row.getField(0);
            String pin = row.getField(2);
            String imsi = ""; //row.getField(2);
            //String memberType = "";//row.getField(3);
            String clearTextPin = "0000";

            switch (OPERATION_MODE) {
                case "MIGRATE": {
                    System.out.println("TID-"+threadId+"Reversing Hash PIN for MSISDN - " + mobileNumber);
                    clearTextPin = DoubleHashCracker.run(pin);

                    if (clearTextPin != null) {
                        System.out.println("TID-"+threadId+"Finished Reversing Hash PIN for MSISDN - " + mobileNumber);
                        CBSAPI.setUserPIN_MIGRATE(UUID.randomUUID().toString(), "MSISDN", mobileNumber, clearTextPin, "IMSI", imsi);
                    } else {
                        System.out.println("TID-"+threadId+"Unable to reverse Hash for MSISDN - " + mobileNumber);
                    }

                    break;
                }

                case "MIGRATE_DISABLE_KYC": {
                    System.out.println("TID-"+threadId+"Reversing Hash PIN for MSISDN - " + mobileNumber);
                    clearTextPin = DoubleHashCracker.run(pin);

                    if (clearTextPin != null) {
                        System.out.println("TID-"+threadId+"Finished Reversing Hash PIN for MSISDN - " + mobileNumber);
                        CBSAPI.setUserPIN_MIGRATE(UUID.randomUUID().toString(), "MSISDN", mobileNumber, clearTextPin, "IMSI", imsi);
                    } else {
                        System.out.println("TID-"+threadId+"Unable to reverse Hash for MSISDN - " + mobileNumber);
                    }

                    System.out.println("TID-"+threadId+"Disable KYC for MSISDN - " + mobileNumber);
                    CBSAPI.disableKYC_MIGRATE(UUID.randomUUID().toString(), "MSISDN", mobileNumber, "", "IMSI", imsi);

                    break;
                }

                case "FORCE_KYC": {
                    System.out.println("TID-"+threadId+"Force KYC for MSISDN - " + mobileNumber);
                    CBSAPI.forceKYC_MIGRATE(UUID.randomUUID().toString(), "MSISDN", mobileNumber, "", "IMSI", imsi);
                    break;
                }

                case "DISABLE_KYC": {
                    System.out.println("TID-"+threadId+"Disable KYC for MSISDN - " + mobileNumber);
                    CBSAPI.disableKYC_MIGRATE(UUID.randomUUID().toString(), "MSISDN", mobileNumber, "", "IMSI", imsi);
                    break;
                }
            }

            System.out.printf("Processed MSISDN: %s:%s:%s - %d/%d (%.2f%%)%n", mobileNumber, pin, clearTextPin, counter, totalThreadRecords, ((double) counter / totalThreadRecords) * 100);

        }
    }
}
