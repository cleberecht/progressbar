package me.tongfei.progressbar;

import java.time.Instant;
import java.util.ArrayList;

/**
 * @author cl
 */
public class Tester {

    public static void main(String[] args) {

        System.out.println("Text before");
        try (ProgressBar pb = new ProgressBar("Test", 10, 70, System.out, ProgressBarStyle.UNICODE_BLOCK, "KB", 1024)) {
            pb.maxHint(5000);
            BitOfInformation sumBit = new BitOfInformation("sum");
            BitOfInformation timeBit = new BitOfInformation("time");
            pb.addBitOfInformation(sumBit);
            pb.addBitOfInformation(timeBit);
            ArrayList<Integer> l = new ArrayList<>();
            for (int i = 0; i < 5000; i++) {
                int sum = 0;
                for (int j = 0; j < i * 2000; j++)
                    sum += j;
                l.add(sum);
                sumBit.setInformation(String.valueOf(sum));
                timeBit.setInformation(Instant.now().toString());
                pb.step();

            }
        }
        System.out.println("Text after");

    }

}
