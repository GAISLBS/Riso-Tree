package bloomfilter;

import java.util.BitSet;

public class MurmurBloomFilter {
    private final int bfSize;
    private final int numOfHash;
    private final int seed;
    private int elementsCount;
    public BitSet bitMap;

    /**
     * Constructor for PrimeBloomFilter and rounding bfSize to the nearest upper multiple of 32
     *
     * @param bfSize    size of the bloom filter bitmap
     * @param numOfHash number of hash functions
     */
    public MurmurBloomFilter(int bfSize, int numOfHash, int seed) {
        this.bfSize = roundUpToNearestMultipleOf32(bfSize);
        this.numOfHash = numOfHash;
        this.seed = seed;
        bitMap = new BitSet(bfSize);
    }

    /**
     * Constructor for MurmurBloomFilter with desired false positive probability and rounding bfSize to the nearest upper multiple of 32.
     *
     * @param elementsCount expected number of elements to be stored
     * @param fpp desired false positive probability
     * @param seed hash function seed
     */
    public MurmurBloomFilter(int elementsCount, double fpp, int seed) {
        this.seed = seed;
        int calculatedBfSize = calcBfSize(elementsCount, fpp);
        this.bfSize = roundUpToNearestMultipleOf32(calculatedBfSize);
        this.numOfHash = calcNumOfHash(bfSize, elementsCount);
        this.bitMap = new BitSet(bfSize);
    }

    /**
     * Constructor for MurmurBloomFilter with desired false positive probability and rounding bfSize to the nearest upper multiple of 32.
     *
     * @param elementsCount expected number of elements to be stored
     * @param fpp desired false positive probability
     * @param seed hash function seed
     * @param numOfHash number of hash functions
     */
    public MurmurBloomFilter(int elementsCount, double fpp, int seed, int numOfHash) {
        this.seed = seed;
        int calculatedBfSize = calcBfSize(elementsCount, fpp);
        this.bfSize = roundUpToNearestMultipleOf32(calculatedBfSize);
        this.numOfHash = numOfHash;
        this.bitMap = new BitSet(bfSize);
    }

    /**
     * Constructor to recreate a MurmurBloomFilter from an int array.
     *
     * @param intArray the int array representing the Bloom filter
     * @param bfSize the size of the Bloom filter bitmap
     * @param numOfHash the number of hash functions
     * @param seed the seed value for hash functions
     */
    public MurmurBloomFilter(int[] intArray, int bfSize, int numOfHash, int seed) {
        this.bfSize = bfSize;
        this.numOfHash = numOfHash;
        this.seed = seed;
        this.bitMap = fromIntArray(intArray);
    }

    private static int calcBfSize(int elementsCount, double fpp) {
        return (int)Math.ceil(-(elementsCount * Math.log(fpp)) / (Math.pow(Math.log(2), 2)));
    }

    private static int calcNumOfHash(int bfSize, int elementsCount) {
        return (int)Math.round(Math.log(2) * bfSize / elementsCount);
    }

    private static int roundUpToNearestMultipleOf32(int number) {
        return (number + 31) & ~31;
    }

    public static BitSet fromIntArray(int[] intArray) {
        int bfSize = intArray.length * 32;
        BitSet bitSet = new BitSet(bfSize);
        for (int i = 0; i < intArray.length; i++) {
            for (int j = 0; j < 32; j++) {
                if ((intArray[i] & (1 << j)) != 0) {
                    int index = i * 32 + j;
                    bitSet.set(index);
                }
            }
        }
        return bitSet;
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

    public String getFpp() {
        double result = Math.pow(1 - Math.exp(-1.0 * numOfHash * elementsCount / bfSize), numOfHash);
        return String.format("%.4f", result);
    }
}
