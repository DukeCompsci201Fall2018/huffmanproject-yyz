import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		/*while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();*/
		
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
		out.close();
	}
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while(true) {
			int index = in.readBits(BITS_PER_WORD);
			if(index == -1) {
				//throw new HuffException("bad input, no PSEUDO_EOF");
				break;
			}else {
				String code = codings[index];
				out.writeBits(code.length(), Integer.parseInt(code, 2));
			}
		}
		
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {
		if(root == null) return;
		
		HuffNode current = root;
		
		if(current.myLeft == null && current.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(1+BITS_PER_WORD, current.myValue);
		}else {
			out.writeBits(1, 0);
			if(root.myLeft!=null) writeHeader(current.myLeft, out);
			if(root.myRight!=null) writeHeader(current.myRight, out);
		}
		
	}

	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		System.out.println(Arrays.toString(encodings));
		return encodings;
	}

	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if(root == null) return;
		
		if(root.myLeft == null && root.myRight == null) {
			//System.out.println("is a leaf");
			encodings[root.myValue] = path;
			//System.out.println(path);
			if(myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("encoding for %d is %s\n", 
									root.myValue, path);
			}
			return;
		}
		
		codingHelper(root.myLeft, path + "0", encodings);
		codingHelper(root.myRight, path + "1", encodings);
	}

	private HuffNode makeTreeFromCounts(int[] freq) {
		//System.out.println("in makeTreeFromCounts");
		//System.out.println(Arrays.toString(freq));
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int index = 0; index < freq.length; index ++) {
			if(freq[index] > 0) {
				pq.add(new HuffNode(index, freq[index], null, null));
			}
		}
		if(myDebugLevel >= DEBUG_HIGH) {
			System.out.printf("pq created with %d nodes\n", pq.size());
		}
		//System.out.println(pq.size());
		while(pq.size() > 1) {
			//System.out.println("In the while loop");
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t= new HuffNode(-1, left.myWeight + right.myWeight, left, right);
			//System.out.println(left.myWeight + right.myWeight);
			pq.add(t);
		}
		
		HuffNode root = pq.remove();
 		return root;
	}

	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		int index = in.readBits(BITS_PER_WORD);
		freq[PSEUDO_EOF] = 1;
		while(index!=-1) {
			//int index = in.readBits(BITS_PER_WORD);
			//if(index == -1) {
				//break;
				//throw new HuffException("bad input, no PSEUDO_EOF");
			//}else {
				/*if(index == PSEUDO_EOF) {
					freq[PSEUDO_EOF] = 1;
					break;
				}else {*/
					freq[index]++;
					
				//}
				index = in.readBits(BITS_PER_WORD);
			//}
		
		}
		for(int i = 0; i < freq.length; i ++) {
			if(myDebugLevel >= DEBUG_HIGH) {
				if(freq[i] != 0)
					System.out.println(i + "\t" + freq[i]);
			}
				
		}
		return freq;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		/*while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();*/
		in.reset();
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
		//System.out.println("test");
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
	
		out.close();
	}

	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while(true) {
			int bits = in.readBits(1);
			if(bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}else {
				if(bits == 0) {
					current = current.myLeft;
				}else {
					current = current.myRight;
				}
				
				if(current!= null && current.myLeft == null && current.myRight == null) {
					if(current.myValue == PSEUDO_EOF) {
						break;
					}else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}

	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if(bit == -1) {
			throw new HuffException("reading bit fails, bit value is " + bit);
		}
		if(bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		}else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}
	
}