package com.lmax.disruptor.util;


import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;
import java.util.Random;


public class LinkedArrayListTest
{
  @Test
  public void iteratorTest()
  {
    Integer[][] arrays = new Integer[6][];

    for (int i = 0; i < 6; i++)
    {
      arrays[i] = new Integer[6];

      for (int j = 0; j < arrays[i].length; j++)
      {
        arrays[i][j] = 2;
      }
    }

    LinkedArrayList<Integer> lal = new LinkedArrayList<>();
    Random random = new Random();

    for (int i = 0; i < 6; i++)
    {
      int before = random.nextInt(13);
      for (int j = 0; j < before; j++)
      {
        lal.addArray(new Integer[]{});
      }

      lal.addArray(arrays[i]);

      int after = random.nextInt(13);
      for (int j = 0; j < after; j++)
      {
        lal.addArray(new Integer[]{});
      }
    }

    Iterator<Integer> it = lal.iterator();

    for (int i = 0; i < 36; i++)
    {
      assert it.next() == 2;
    }

    assert !it.hasNext();

    int count = 0;
    for (Integer i : lal)
    {
      count++;
      assert  i == 2;
    }

    assert count == 36;
  }

}
