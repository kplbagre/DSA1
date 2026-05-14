import static java.lang.Math.max;

import com.sun.source.tree.Tree;

public class Treenode {
    int value;
    Treenode left;
    Treenode right;

    Treenode () {};
    Treenode (int val)
    {
        this.value = val;
    }
    Treenode (int val, Treenode left, Treenode right)
    {
        this.value = val;
        this.left = left;
        this.right = right;
    }
    public  static void main(String a[])
    {

        Treenode t = new Treenode(1);
        Treenode left = new Treenode( 2);
        Treenode right = new Treenode(3);
        t.left = left;
        left.left = new Treenode( 5);
        t.right = right;
        right.left = new Treenode(6);
        right.right = new Treenode(7);
        //inOrder(t);
        //System.out.println(maxDepth(t));
        dfs(t);
    }
    public static void inOrder(Treenode tr)
    {
            if (tr== null)
                return;
            if (tr.left == null && tr.right == null)
            {
                System.out.println(tr.value);

            }
            else
            {
               inOrder(tr.left);
               System.out.println(tr.value);
               inOrder(tr.right);
            }
    }

    public static int maxDepth(Treenode t)
    {
        if (t == null)
        {
            return 0;
        }
        int leftDepth = maxDepth(t.left);
        int rightDepth = maxDepth(t.right);

        return 1 + max(leftDepth, rightDepth);

    }

    public static void dfs (Treenode t)
    {
        if (t == null)
        {
            return;
        }
        //System.out.println(t.value); //pre order
        dfs(t.left);
        System.out.println(t.value); // in order
        dfs(t.right);
        //System.out.println(t.value); // post order
    }


}



