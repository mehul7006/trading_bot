import java.util.Scanner;
public class TestBot {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("Bot ready. Type something:");
        while(true) {
            String input = sc.nextLine();
            if(input.equals("/start")) {
                System.out.println("START COMMAND RECEIVED AND WORKING!");
                break;
            } else if(input.equals("quit")) {
                break;
            } else {
                System.out.println("You typed: " + input);
            }
        }
        sc.close();
    }
}
