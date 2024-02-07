package bloomfilter;

import java.io.*;
import java.util.ArrayList;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

public class GuavaBloomFilter implements Serializable {
    private int size = 5;
    private double falsePositiveProbability = 0.01;
    public long[] data;

    private BloomFilter<Integer> filter;

    public GuavaBloomFilter(int size, double falsePositiveProbability) {
        this.size = size;
        this.falsePositiveProbability = falsePositiveProbability;
        this.filter = BloomFilter.create(Funnels.integerFunnel(), this.size, this.falsePositiveProbability);
    }

    public void add(int... ids) {
        for (int id : ids) {
            filter.put(id);
        }
    }

    public void add(ArrayList<Integer> ids) {
        for (int id : ids) {
            filter.put(id);
        }
    }

    public boolean contains(int id) {
        return filter.mightContain(id);
    }

//    public long[] getSerializedBitSet() {
//        return filter.bits.data.array;
//    }


    public BloomFilter<Integer> getFilter() {
        return filter;
    }

}
