package ke.skyworld.mbanking.nav;

import ke.skyworld.mbanking.mappapi.MAPPAPIDB;
import ke.skyworld.mbanking.ussdapi.APIUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScheduledTasks {

    /*public static void startNavTransactionPoster(long intervalPeriod){
        try{
            ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
            service.scheduleAtFixedRate(() -> {
                try {
                    APIUtils.hashPINsOnNAV();
                } catch (Exception e) {
                    System.err.println("ScheduledTasks.hashPINsOnNAV() Error: " + e.getMessage());
                }
            }, 0, intervalPeriod, TimeUnit.SECONDS);

            ScheduledExecutorService firstService = Executors.newSingleThreadScheduledExecutor();
            firstService.scheduleAtFixedRate(() -> {
                try {
                    Navision.getPort().callServiceFunction(1);
                } catch (Exception e) {
                    System.err.println("ScheduledTasks.NavTransactionsPoster(1) Error: " + e.getMessage());
                }
            }, 0, intervalPeriod, TimeUnit.SECONDS);

            ScheduledExecutorService secondService = Executors.newSingleThreadScheduledExecutor();
            secondService.scheduleAtFixedRate(() -> {
                try {
                    Navision.getPort().callServiceFunction(2);
                    Thread.sleep(intervalPeriod*1000);
                } catch (Exception e) {
                    System.err.println("ScheduledTasks.NavTransactionsPoster(2) Error: " + e.getMessage());
                }
            }, 0, intervalPeriod, TimeUnit.SECONDS);

            ScheduledExecutorService thirdService = Executors.newSingleThreadScheduledExecutor();
            thirdService.scheduleAtFixedRate(() -> {
                try {
                    Navision.getPort().callServiceFunction(3);
                } catch (Exception e) {
                    System.err.println("ScheduledTasks.NavTransactionsPoster(3) Error: " + e.getMessage());
                }
            }, 0, intervalPeriod, TimeUnit.SECONDS);

            ScheduledExecutorService fourthService = Executors.newSingleThreadScheduledExecutor();
            fourthService.scheduleAtFixedRate(() -> {
                try {
                    Navision.getPort().callServiceFunction(4);
                } catch (Exception e) {
                    System.err.println("ScheduledTasks.NavTransactionsPoster(4) Error: " + e.getMessage());
                }
            }, 0, intervalPeriod, TimeUnit.SECONDS);

            ScheduledExecutorService fifthService = Executors.newSingleThreadScheduledExecutor();
            fifthService.scheduleAtFixedRate(() -> {
                try {
                    Navision.getPort().callServiceFunction(5);
                } catch (Exception e) {
                    System.err.println("ScheduledTasks.NavTransactionsPoster(5) Error: " + e.getMessage());
                }
            }, 0, intervalPeriod, TimeUnit.SECONDS);

            ScheduledExecutorService sixthService = Executors.newSingleThreadScheduledExecutor();
            sixthService.scheduleAtFixedRate(() -> {
                try {
                    Navision.getPort().callServiceFunction(6);
                } catch (Exception e) {
                    System.err.println("ScheduledTasks.NavTransactionsPoster(6) Error: " + e.getMessage());
                }
            }, 0, intervalPeriod, TimeUnit.SECONDS);

            ScheduledExecutorService seventhService = Executors.newSingleThreadScheduledExecutor();
            seventhService.scheduleAtFixedRate(() -> {
                try {
                    Navision.getPort().callServiceFunction(7);
                } catch (Exception e) {
                    System.err.println("ScheduledTasks.NavTransactionsPoster(7) Error: " + e.getMessage());
                }
            }, 0, intervalPeriod, TimeUnit.SECONDS);

            ScheduledExecutorService eigthService = Executors.newSingleThreadScheduledExecutor();
            eigthService.scheduleAtFixedRate(() -> {
                try {
                    Navision.getPort().callServiceFunction(8);
                } catch (Exception e) {
                    System.err.println("ScheduledTasks.NavTransactionsPoster(8) Error: " + e.getMessage());
                }
            }, 0, intervalPeriod, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("ScheduledTasks.startNavTransactionPoster() Error: " + e.getMessage());
        }
    }*/

 /*   public static void startNavTransactionPoster(){
        try {
            Thread worker1 = new Thread(() -> {
                while(true){
                    for (int i = 1; i <= 8; i++) {
                        try {
                            //System.out.println("ScheduledTasks.callServiceFunction("+i+") Calling function...");
                            Instant instStart;
                            Instant instEnd;
                            instStart = Instant.now();
                            Navision.callServiceFunction(i);
                            instEnd = Instant.now();
                            Duration durTimeElapsed = Duration.between(instStart, instEnd);
                            //System.out.println("ScheduledTasks.callServiceFunction("+i+") Responded after: " + durTimeElapsed.getSeconds() +" Seconds");
                            instStart = null;
                            instEnd = null;
                            durTimeElapsed = null;
                        } catch (Exception e) {
                            System.err.println("ScheduledTasks.callServiceFunction("+i+") Error: " + e.getMessage());
                        }
                    }
                    try {
                        Thread.sleep(5 * 1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            worker1.start();

            Thread worker2 = new Thread(() -> {
                while(true){
                    try {
                        APIUtils.hashPINsOnNAV();
                        Thread.sleep(300 * 1000);
                    } catch (Exception e) {
                        System.err.println("ScheduledTasks.hashPINsOnNAV() Error: " + e.getMessage());
                    }
                }
            });
            worker2.start();

        } catch (Exception e){
            System.err.println("ScheduledTasks.startNavTransactionPoster() Error: " + e.getMessage());
        } finally {

        }
    }*/

    public static void startNavAgencyTransactionPoster(){
        try{
            Thread worker1 = new Thread(() -> {
                for (int i = 1; i <= 5; i++) {
                    try {
                        APIUtils.hashAgencyPINsOnNAV();
                        Thread.sleep(300 * 1000);
                    } catch (Exception e) {
                        System.err.println("ScheduledTasks.callServiceFunction("+i+") Error: " + e.getMessage());
                    }
                }
            });
            worker1.start();

            Thread worker2 = new Thread(() -> {
                while(true){
                    try {
                        MAPPAPIDB.fnDeleteOTPDataAfterTimeOut();
                    } catch (Exception e) {
                        System.err.println("ScheduledTasks.hashPINsOnNAV() Error: " + e.getMessage());
                    }
                    try {
                        Thread.sleep(300 * 1000);
                    } catch (Exception e) {
                        System.err.println("ScheduledTasks.hashPINsOnNAV() Error: " + e.getMessage());
                    }
                }
            });
            worker2.start();

            Thread worker3 = new Thread(() -> {
                while(true){
                    try {
                        NavisionAgency.postMpesaTransaction("");
                    } catch (Exception e) {
                        System.err.println("ScheduledTasks.fixMSGs() Error: " + e.getMessage());
                    }
                    try {
                        Thread.sleep(300 * 1000);
                    } catch (Exception e) {
                        System.err.println("ScheduledTasks.fixMSGs() Error: " + e.getMessage());
                    }
                }
            });
            worker3.start();
        } catch (Exception e) {
            System.err.println("ScheduledTasks.startNavAgencyTransactionPoster() Error: " + e.getMessage());
        } finally {

        }
    }



}
