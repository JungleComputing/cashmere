package example.mmult;

import java.util.List;
import java.nio.ByteBuffer;

import ibis.cashmere.constellation.Buffer;

//
// Class Matrix
//
// recursive part of quad tree
//
final class Matrix extends Leaf implements java.io.Serializable {
    Matrix _00, _01, _10, _11;
    int task, rec, loop;
    boolean flipped;
    float dbl;

    @Override
    public void getByteBuffers(List<ByteBuffer> byteBuffers) {
	if (_00 == null) {
	    super.getByteBuffers(byteBuffers);
	    return;
	}
	_00.getByteBuffers(byteBuffers);
	_01.getByteBuffers(byteBuffers);
	_10.getByteBuffers(byteBuffers);
	_11.getByteBuffers(byteBuffers);
    }

    @Override
    public void setByteBuffers(List<ByteBuffer> byteBuffers) {
	if (_00 == null) {
	    super.setByteBuffers(byteBuffers);
	    return;
	}
	_00.setByteBuffers(byteBuffers);
	_01.setByteBuffers(byteBuffers);
	_10.setByteBuffers(byteBuffers);
	_11.setByteBuffers(byteBuffers);
    }

    @Override
    public boolean clearBuffer(Buffer b) {
	if (_00 == null) {
	    return super.clearBuffer(b);
	}
	return _00.clearBuffer(b) || _01.clearBuffer(b)
		|| _10.clearBuffer(b) || _11.clearBuffer(b);
    }

    Matrix allocateSub(boolean flip) {
	int t = task;
	int r = rec;
	if (t == 0) {
	    r--;
	} else {
	    t--;
	}

	return new Matrix(t, r, loop, (flip && flipped) ? -dbl : dbl, flipped);
    }

    void allocateSubs() {
	if (_00 == null) {
	    _00 = allocateSub(false);
	}
	if (_01 == null) {
	    _01 = allocateSub(true);
	}
	if (_10 == null) {
	    _10 = allocateSub(false);
	}
	if (_11 == null) {
	    _11 = allocateSub(true);
	}
    }

    Matrix getSubMatrix(byte[] pos) {
	Matrix sub;
	boolean flip = false;

	if (pos[0] == 00) {
	    sub = _00;
	} else if (pos[0] == 01) {
	    sub = _01;
	    flip = true;
	} else if (pos[0] == 10) {
	    sub = _10;
	} else if (pos[0] == 11) {
	    sub = _11;
	    flip = true;
	} else {
	    throw new Error("internal error");
	}

	if (sub == null) {
	    sub = allocateSub(flip);
	    if (pos[0] == 00) {
		_00 = sub;
	    } else if (pos[0] == 01) {
		_01 = sub;
	    } else if (pos[0] == 10) {
		_10 = sub;
	    } else /* if(pos[0] == 11) */{
		_11 = sub;
	    }
	}

	if (pos.length == 1) {
	    return sub;
	}

	byte[] newPos = new byte[pos.length - 1];
	System.arraycopy(pos, 0, newPos, 0, newPos.length);

	return sub.getSubMatrix(newPos);
    }

    public Matrix(int task, int rec, int loop, float dbl, boolean flipped) {
	this(task, rec, loop, dbl, flipped, false);
    }

    public Matrix(int task, int rec, int loop, float dbl, boolean flipped,
	    boolean allocateNow) {
	super();
	this.task = task;
	this.rec = rec;
	this.loop = loop;
	this.dbl = dbl;
	this.flipped = flipped;

	if (allocateNow) {
	    if (task + rec <= 0) {
		allocateLeaf(loop, dbl, flipped);
		return;
	    }

	    // Quad tree is recursive for both the task and the serial
	    // recursion levels

	    if (task > 0) {
		task--;
	    } else {
		rec--;
	    }

	    _00 = new Matrix(task, rec, loop, dbl, flipped, allocateNow);
	    _01 = new Matrix(task, rec, loop, (flipped ? -dbl : dbl), flipped,
		    allocateNow);
	    _10 = new Matrix(task, rec, loop, dbl, flipped, allocateNow);
	    _11 = new Matrix(task, rec, loop, (flipped ? -dbl : dbl), flipped,
		    allocateNow);
	}
    }

    public void print(int task, int rec) {
	if (task + rec > 0) {
	    if (task > 0) {
		task--;
	    } else {
		rec--;
	    }

	    _00.print(task, rec);
	    _01.print(task, rec);
	    _10.print(task, rec);
	    _11.print(task, rec);
	} else {
	    super.print();
	}
    }

    public boolean check(int task, int rec, float result) {
	boolean ok = true;

	if (task + rec > 0) {
	    if (task > 0) {
		task--;
	    } else {
		rec--;
	    }

	    ok &= _00.check(task, rec, result);
	    ok &= _01.check(task, rec, result);
	    ok &= _10.check(task, rec, result);
	    ok &= _11.check(task, rec, result);
	} else {
	    ok &= super.check(result);
	}

	return ok;
    }

    public void matMul(Matrix a, Matrix b, boolean gpu) {
	// pass Matrices as local variables
	// loopMatMul(a, b);
	// multiplyStride2(a, b);
	allocateLeafBB(loop, dbl, flipped);
	// allocateLeaf(loop, dbl, flipped);
	if (gpu) {
	    multiplyMCL(a, b);
	} else {
	    // loopMatMul(a, b);
	    multiplyStride2(a, b);
	}
    }
}
