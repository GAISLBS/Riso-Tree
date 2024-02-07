package bloomfilter;

import java.util.BitSet;

public class MurmurBloomFilter {
    private final int bfSize;
    private final int numOfHash;
    private final int seed;
    private int elementsCount;
    public BitSet bitMap;

    /**
     * Constructor for PrimeBloomFilter
     *
     * @param bfSize      size of the bloom filter bitmap
     * @param numOfHash number of hash functions
     */
    public MurmurBloomFilter(int bfSize, int numOfHash, int seed) {
        this.bfSize = bfSize;
        this.numOfHash = numOfHash;
        this.seed = seed;
        bitMap = new BitSet(bfSize);
    }

    public void setElementsCount(int elementsCount) {
        this.elementsCount = elementsCount;
    }

    public int getElementsCount() {
        return elementsCount;
    }

    public void add(int key) {
        for (int i = 0; i < numOfHash; i++) {
            int curSeed = seed + i;
            int hash = Murmur3.hashInt(key, curSeed);
            int position = Math.abs(hash) % bfSize;
            bitMap.set(position);
        }
        elementsCount++;
    }

    public boolean contains(int key) {
        for (int i = 0; i < numOfHash; i++) {
            int curSeed = seed + i;
            int hash = Murmur3.hashInt(key, curSeed);
            int position = Math.abs(hash) % bfSize;
            if (!bitMap.get(position)) {
                return false;
            }
        }
        return true;
    }

    public int[] toIntArray() {
        int intSize = (bitMap.length() + 31) / 32;
        int[] intArray = new int[intSize];
        for (int i = 0; i < bitMap.length(); i++) {
            if (bitMap.get(i)) {
                intArray[i / 32] |= (1 << (i % 32));
            }
        }
        return intArray;
    }


    public byte[] toByteArray() {
        return bitMap.toByteArray();
    }

    public static BitSet fromByteArray(byte[] bytes) {
        return BitSet.valueOf(bytes);
    }

    public double getFpp() {
        return Math.pow(1 - Math.exp(-1.0 * numOfHash * elementsCount / bfSize), numOfHash);
    }
}
