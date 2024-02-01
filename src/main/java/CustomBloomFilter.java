import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

public class CustomBloomFilter {
    private int size = 5;
    private double falsePositiveProbability = 0.01;

    private BloomFilter<Integer> filter;

    public CustomBloomFilter(int size, double falsePositiveProbability) {
        this.size = size;
        this.falsePositiveProbability = falsePositiveProbability;
        this.filter = BloomFilter.create(Funnels.integerFunnel(), this.size, this.falsePositiveProbability);
    }

    public void add(int... ids) {
        for (int id : ids) {
            filter.put(id);
        }
    }

    public boolean contains(int id) {
        return filter.mightContain(id);
    }

    public BloomFilter<Integer> getFilter() {
        return filter;
    }

}
