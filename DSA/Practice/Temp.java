import java.util.*;

public class Temp{
    public static void main (String[] args){

        int n = 10;
        Integer [] mem = new Integer[n+1];

        //int ans = wayClimb(n,mem);

        mem[1] = 1;
        mem[2] = 2;

        for (int i=3; i<=n ; i++)
        {
            mem[i] = mem[i-1] + mem[i-2];
        }

        System.out.println(mem[n]);

    }


    public static int wayClimb(int n, Integer[] mem)
    {
        if (n<=2)
        {return n;}

        if (mem[n]== null)
        {
            mem[n] = wayClimb(n-1,mem) + wayClimb(n-2,mem);
        }

        return mem[n];
    }

}