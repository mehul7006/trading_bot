package com.trading.bot.test;
import java.io.FileWriter;
public class SimpleTest {
    public static void main(String[] args) throws Exception {
        try (FileWriter fw = new FileWriter("simple_test.txt")) {
            fw.write("Hello from SimpleTest");
        }
    }
}
