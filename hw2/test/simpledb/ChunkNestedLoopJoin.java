package simpledb;


import java.util.NoSuchElementException;

/**
 * The ChunkNestedLoopJoin operator implements the chunk nested loop join operation.
 */
public class ChunkNestedLoopJoin extends Operator {

    private static final long serialVersionUID = 1L;
    private JoinPredicate pred;
    private DbIterator child1, child2;
    private TupleDesc comboTD;
    private Chunk chunk;
    private int chunkSize;
    private int chunkPointer;

    /**
     * Constructor. Accepts to children to join and the predicate to join them
     * on
     *
     * @param p
     *            The predicate to use to join the children
     * @param child1
     *            Iterator for the left(outer) relation to join
     * @param child2
     *            Iterator for the right(inner) relation to join
     * @param chunkSize
     *            The chunk size used for chunk nested loop join
     */
    public ChunkNestedLoopJoin(JoinPredicate p, DbIterator child1, DbIterator child2, int chunkSize) {
        this.pred = p;
        this.child1 = child1;
        this.child2 = child2;
        this.chunkSize = chunkSize;
        comboTD = TupleDesc.merge(child1.getTupleDesc(), child2.getTupleDesc());
        chunkPointer = 0;
    }

    public JoinPredicate getJoinPredicate() {
        return pred;
    }

    public TupleDesc getTupleDesc() {
        return comboTD;
    }

    /**
     * Opens the iterator.
     */
    public void open() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        child1.open();
        child2.open();
        super.open();
    }

    /**
     * Closes the iterator.
     */
    public void close() {
        super.close();
        child2.close();
        child1.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        child1.rewind();
        child2.rewind();
        chunkPointer = 0;
    }

    /**
     * Returns the current chunk. 
     */
    public Chunk getCurrentChunk() throws DbException, TransactionAbortedException {
        return chunk;
    }

    /**
     * Updates the current chunk with the next set of Tuples and returns the chunk.
     */
    protected Chunk fetchNextChunk() throws DbException, TransactionAbortedException {
        Chunk chunk = new Chunk(chunkSize);
        chunk.loadChunk(child1);
        this.chunk = chunk;
        return chunk;
    }

    /**
     * Returns the next tuple generated by the join, or null if there are no
     * more tuples. Logically, this is the next tuple in r1 cross r2 that
     * satisfies the join predicate. 
     *
     * Note that the tuples returned from this particular implementation
     * are simply the concatenation of joining tuples from the left and right
     * relation. Therefore, if an equality predicate is used there will be two
     * copies of the join attribute in the results.
     *
     * For example, if one tuple is {1,2,3} and the other tuple is {1,5,6},
     * joined on equality of the first column, then this returns {1,2,3,1,5,6}.
     *
     * @return The next matching tuple.
     * @see JoinPredicate#filter
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        while (child1.hasNext()) {
            Chunk c1;
            if (getCurrentChunk() == null) {
                c1 = fetchNextChunk();
                chunkPointer = 0;
            } else {
                c1 = getCurrentChunk();
            }

            Tuple[] tuples = c1.getChunkTuples();

            int count = chunkPointer;
            //perform checks on a portion of the chunk
            while (count < chunkSize) {
                Tuple t1 = tuples[count];

                // loop around child2
                while (child2.hasNext()) {
                    Tuple t2 = child2.next();

                    // if match, create a combined tuple and fill it with the values
                    // from both tuples
                    if (!pred.filter(t1, t2))
                        continue;

                    int td1n = t1.getTupleDesc().numFields();
                    int td2n = t2.getTupleDesc().numFields();

                    // set fields in combined tuple
                    Tuple t = new Tuple(comboTD);
                    for (int i = 0; i < td1n; i++)
                        t.setField(i, t1.getField(i));
                    for (int i = 0; i < td2n; i++)
                        t.setField(td1n + i, t2.getField(i));
                    return t;
                }
                // child2 is done: advance child1
                child2.rewind();
                count += 1;
                chunkPointer += 1;
                // if a chunk has been cleared, reset chunk
                if (count == chunkSize) {
                    this.chunk = null;
                }
            }
        }
        return null;
    }


    @Override
    public DbIterator[] getChildren() {
        return new DbIterator[] { this.child1, this.child2 };
    }

    @Override
    public void setChildren(DbIterator[] children) {
        this.child1 = children[0];
        this.child2 = children[1];
    }
}