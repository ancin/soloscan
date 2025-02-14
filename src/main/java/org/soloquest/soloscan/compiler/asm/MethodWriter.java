/***
 * ASM: a very small and fast Java bytecode manipulation framework Copyright (c) 2000-2011 INRIA,
 * France Telecom All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met: 1. Redistributions of source codegen must retain the
 * above copyright notice, this list of conditions and the following disclaimer. 2. Redistributions
 * in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.soloquest.soloscan.compiler.asm;

/**
 * A {@link MethodVisitor} that generates methods in bytecode form. Each visit method of this class
 * appends the bytecode corresponding to the visited instruction to a byte vector, in the order
 * these methods are called.
 *
 * @author Eric Bruneton
 * @author Eugene Kuleshov
 */
class MethodWriter extends MethodVisitor {

    /**
     * Pseudo access flag used to denote constructors.
     */
    static final int ACC_CONSTRUCTOR = 0x80000;

    /**
     * Frame has exactly the same locals as the previous stack map frame and number of stack items is
     * zero.
     */
    static final int SAME_FRAME = 0; // to 63 (0-3f)

    /**
     * Frame has exactly the same locals as the previous stack map frame and number of stack items is
     * 1
     */
    static final int SAME_LOCALS_1_STACK_ITEM_FRAME = 64; // to 127 (40-7f)

    /**
     * Reserved for future use
     */
    static final int RESERVED = 128;

    /**
     * Frame has exactly the same locals as the previous stack map frame and number of stack items is
     * 1. Offset is bigger then 63;
     */
    static final int SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED = 247; // f7

    /**
     * Frame where current locals are the same as the locals in the previous frame, except that the k
     * last locals are absent. The value of k is given by the formula 251-frame_type.
     */
    static final int CHOP_FRAME = 248; // to 250 (f8-fA)

    /**
     * Frame has exactly the same locals as the previous stack map frame and number of stack items is
     * zero. Offset is bigger then 63;
     */
    static final int SAME_FRAME_EXTENDED = 251; // fb

    /**
     * Frame where current locals are the same as the locals in the previous frame, except that k
     * additional locals are defined. The value of k is given by the formula frame_type-251.
     */
    static final int APPEND_FRAME = 252; // to 254 // fc-fe

    /**
     * Full frame
     */
    static final int FULL_FRAME = 255; // ff

    /**
     * Indicates that the stack map frames must be recomputed from scratch. In this case the maximum
     * stack size and number of local variables is also recomputed from scratch.
     *
     * @see #compute
     */
    private static final int FRAMES = 0;

    /**
     * Indicates that the maximum stack size and number of local variables must be automatically
     * computed.
     *
     * @see #compute
     */
    private static final int MAXS = 1;

    /**
     * Indicates that nothing must be automatically computed.
     *
     * @see #compute
     */
    private static final int NOTHING = 2;

    /**
     * The class writer to which this method must be added.
     */
    final ClassWriter cw;

    /**
     * Access flags of this method.
     */
    private int access;

    /**
     * The index of the constant pool item that contains the name of this method.
     */
    private final int name;

    /**
     * The index of the constant pool item that contains the descriptor of this method.
     */
    private final int desc;

    /**
     * The descriptor of this method.
     */
    private final String descriptor;

    /**
     * The signature of this method.
     */
    String signature;

    /**
     * If not zero, indicates that the codegen of this method must be copied from the ClassReader
     * associated to this writer in <codegen>cw.cr</codegen>. More precisely, this field gives the index of
     * the first byte to copied from <codegen>cw.cr.b</codegen>.
     */
    int classReaderOffset;

    /**
     * If not zero, indicates that the codegen of this method must be copied from the ClassReader
     * associated to this writer in <codegen>cw.cr</codegen>. More precisely, this field gives the number of
     * bytes to copied from <codegen>cw.cr.b</codegen>.
     */
    int classReaderLength;

    /**
     * Number of exceptions that can be thrown by this method.
     */
    int exceptionCount;

    /**
     * The exceptions that can be thrown by this method. More precisely, this array contains the
     * indexes of the constant pool items that contain the internal names of these exception classes.
     */
    int[] exceptions;

    /**
     * The annotation default attribute of this method. May be <tt>null</tt>.
     */
    private ByteVector annd;

    /**
     * The lang visible annotations of this method. May be <tt>null</tt>.
     */
    private AnnotationWriter anns;

    /**
     * The lang invisible annotations of this method. May be <tt>null</tt>.
     */
    private AnnotationWriter ianns;

    /**
     * The lang visible parameter annotations of this method. May be <tt>null</tt>.
     */
    private AnnotationWriter[] panns;

    /**
     * The lang invisible parameter annotations of this method. May be <tt>null</tt>.
     */
    private AnnotationWriter[] ipanns;

    /**
     * The number of synthetic parameters of this method.
     */
    private int synthetics;

    /**
     * The non standard attributes of the method.
     */
    private Attribute attrs;

    /**
     * The bytecode of this method.
     */
    private ByteVector code = new ByteVector();

    /**
     * Maximum stack size of this method.
     */
    private int maxStack;

    /**
     * Maximum number of local variables for this method.
     */
    private int maxLocals;

    /**
     * Number of local variables in the current stack map frame.
     */
    private int currentLocals;

    /**
     * Number of stack map frames in the StackMapTable attribute.
     */
    private int frameCount;

    /**
     * The StackMapTable attribute.
     */
    private ByteVector stackMap;

    /**
     * The offset of the last frame that was written in the StackMapTable attribute.
     */
    private int previousFrameOffset;

    /**
     * The last frame that was written in the StackMapTable attribute.
     *
     * @see #frame
     */
    private int[] previousFrame;

    /**
     * The current stack map frame. The first element contains the offset of the instruction to which
     * the frame corresponds, the second element is the number of locals and the third one is the
     * number of stack elements. The local variables start at index 3 and are followed by the operand
     * stack values. In summary frame[0] = offset, frame[1] = nLocal, frame[2] = nStack, frame[3] =
     * nLocal. All types are encoded as integers, with the same format as the one used in
     * {@link Label}, but limited to BASE types.
     */
    private int[] frame;

    /**
     * Number of elements in the exception handler list.
     */
    private int handlerCount;

    /**
     * The first element in the exception handler list.
     */
    private Handler firstHandler;

    /**
     * The last element in the exception handler list.
     */
    private Handler lastHandler;

    /**
     * Number of entries in the LocalVariableTable attribute.
     */
    private int localVarCount;

    /**
     * The LocalVariableTable attribute.
     */
    private ByteVector localVar;

    /**
     * Number of entries in the LocalVariableTypeTable attribute.
     */
    private int localVarTypeCount;

    /**
     * The LocalVariableTypeTable attribute.
     */
    private ByteVector localVarType;

    /**
     * Number of entries in the LineNumberTable attribute.
     */
    private int lineNumberCount;

    /**
     * The LineNumberTable attribute.
     */
    private ByteVector lineNumber;

    /**
     * The non standard attributes of the method's codegen.
     */
    private Attribute cattrs;

    /**
     * Indicates if some jump instructions are too small and need to be resized.
     */
    private boolean resize;

    /**
     * The number of subroutines in this method.
     */
    private int subroutines;

    // ------------------------------------------------------------------------

    /*
     * Fields for the control flow graph analysis algorithm (used to compute the maximum stack size).
     * A control flow graph contains one node per "basic block", and one edge per "jump" from one
     * basic block to another. Each node (i.e., each basic block) is represented by the Label object
     * that corresponds to the first instruction of this basic block. Each node also stores the list
     * of its successors in the graph, as a linked list of Edge objects.
     */

    /**
     * Indicates what must be automatically computed.
     *
     * @see #FRAMES
     * @see #MAXS
     * @see #NOTHING
     */
    private final int compute;

    /**
     * A list of labels. This list is the list of basic blocks in the method, i.e. a list of Label
     * objects linked to each other by their {@link Label#successor} field, in the order they are
     * visited by {@link MethodVisitor#visitLabel}, and starting with the first basic block.
     */
    private Label labels;

    /**
     * The previous basic block.
     */
    private Label previousBlock;

    /**
     * The current basic block.
     */
    private Label currentBlock;

    /**
     * The (relative) stack size after the last visited instruction. This size is relative to the
     * beginning of the current basic block, i.e., the true stack size after the last visited
     * instruction is equal to the {@link Label#inputStackTop beginStackSize} of the current basic
     * block plus <tt>stackSize</tt>.
     */
    private int stackSize;

    /**
     * The (relative) maximum stack size after the last visited instruction. This size is relative to
     * the beginning of the current basic block, i.e., the true maximum stack size after the last
     * visited instruction is equal to the {@link Label#inputStackTop beginStackSize} of the current
     * basic block plus <tt>stackSize</tt>.
     */
    private int maxStackSize;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructs a new {@link MethodWriter}.
     *
     * @param cw            the class writer in which the method must be added.
     * @param access        the method's access flags (see {@link Opcodes}).
     * @param name          the method's name.
     * @param desc          the method's descriptor (see {@link Type}).
     * @param signature     the method's signature. May be <tt>null</tt>.
     * @param exceptions    the internal names of the method's exceptions. May be <tt>null</tt>.
     * @param computeMaxs   <tt>true</tt> if the maximum stack size and number of local variables must
     *                      be automatically computed.
     * @param computeFrames <tt>true</tt> if the stack map tables must be recomputed from scratch.
     */
    MethodWriter(final ClassWriter cw, final int access, final String name, final String desc,
                 final String signature, final String[] exceptions, final boolean computeMaxs,
                 final boolean computeFrames) {
        super(Opcodes.ASM4);
        if (cw.firstMethod == null) {
            cw.firstMethod = this;
        } else {
            cw.lastMethod.mv = this;
        }
        cw.lastMethod = this;
        this.cw = cw;
        this.access = access;
        if ("<init>".equals(name)) {
            this.access |= ACC_CONSTRUCTOR;
        }
        this.name = cw.newUTF8(name);
        this.desc = cw.newUTF8(desc);
        this.descriptor = desc;
        if (ClassReader.SIGNATURES) {
            this.signature = signature;
        }
        if (exceptions != null && exceptions.length > 0) {
            this.exceptionCount = exceptions.length;
            this.exceptions = new int[this.exceptionCount];
            for (int i = 0; i < this.exceptionCount; ++i) {
                this.exceptions[i] = cw.newClass(exceptions[i]);
            }
        }
        this.compute = computeFrames ? FRAMES : (computeMaxs ? MAXS : NOTHING);
        if (computeMaxs || computeFrames) {
            // updates maxLocals
            int size = Type.getArgumentsAndReturnSizes(this.descriptor) >> 2;
            if ((access & Opcodes.ACC_STATIC) != 0) {
                --size;
            }
            this.maxLocals = size;
            this.currentLocals = size;
            // creates and visits the label for the first basic block
            this.labels = new Label();
            this.labels.status |= Label.PUSHED;
            visitLabel(this.labels);
        }
    }

    // ------------------------------------------------------------------------
    // Implementation of the MethodVisitor abstract class
    // ------------------------------------------------------------------------

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
        if (!ClassReader.ANNOTATIONS) {
            return null;
        }
        this.annd = new ByteVector();
        return new AnnotationWriter(this.cw, false, this.annd, null, 0);
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
        if (!ClassReader.ANNOTATIONS) {
            return null;
        }
        ByteVector bv = new ByteVector();
        // write type, and reserve space for values count
        bv.putShort(this.cw.newUTF8(desc)).putShort(0);
        AnnotationWriter aw = new AnnotationWriter(this.cw, true, bv, bv, 2);
        if (visible) {
            aw.next = this.anns;
            this.anns = aw;
        } else {
            aw.next = this.ianns;
            this.ianns = aw;
        }
        return aw;
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(final int parameter, final String desc,
                                                      final boolean visible) {
        if (!ClassReader.ANNOTATIONS) {
            return null;
        }
        ByteVector bv = new ByteVector();
        if ("Ljava/lang/Synthetic;".equals(desc)) {
            // workaround for a bug in javac with synthetic parameters
            // see ClassReader.readParameterAnnotations
            this.synthetics = Math.max(this.synthetics, parameter + 1);
            return new AnnotationWriter(this.cw, false, bv, null, 0);
        }
        // write type, and reserve space for values count
        bv.putShort(this.cw.newUTF8(desc)).putShort(0);
        AnnotationWriter aw = new AnnotationWriter(this.cw, true, bv, bv, 2);
        if (visible) {
            if (this.panns == null) {
                this.panns = new AnnotationWriter[Type.getArgumentTypes(this.descriptor).length];
            }
            aw.next = this.panns[parameter];
            this.panns[parameter] = aw;
        } else {
            if (this.ipanns == null) {
                this.ipanns = new AnnotationWriter[Type.getArgumentTypes(this.descriptor).length];
            }
            aw.next = this.ipanns[parameter];
            this.ipanns[parameter] = aw;
        }
        return aw;
    }

    @Override
    public void visitAttribute(final Attribute attr) {
        if (attr.isCodeAttribute()) {
            attr.next = this.cattrs;
            this.cattrs = attr;
        } else {
            attr.next = this.attrs;
            this.attrs = attr;
        }
    }

    @Override
    public void visitCode() {
    }

    @Override
    public void visitFrame(final int type, final int nLocal, final Object[] local, final int nStack,
                           final Object[] stack) {
        if (!ClassReader.FRAMES || this.compute == FRAMES) {
            return;
        }

        if (type == Opcodes.F_NEW) {
            if (this.previousFrame == null) {
                visitImplicitFirstFrame();
            }
            this.currentLocals = nLocal;
            int frameIndex = startFrame(this.code.length, nLocal, nStack);
            for (int i = 0; i < nLocal; ++i) {
                if (local[i] instanceof String) {
                    this.frame[frameIndex++] = Frame.OBJECT | this.cw.addType((String) local[i]);
                } else if (local[i] instanceof Integer) {
                    this.frame[frameIndex++] = ((Integer) local[i]).intValue();
                } else {
                    this.frame[frameIndex++] =
                            Frame.UNINITIALIZED | this.cw.addUninitializedType("", ((Label) local[i]).position);
                }
            }
            for (int i = 0; i < nStack; ++i) {
                if (stack[i] instanceof String) {
                    this.frame[frameIndex++] = Frame.OBJECT | this.cw.addType((String) stack[i]);
                } else if (stack[i] instanceof Integer) {
                    this.frame[frameIndex++] = ((Integer) stack[i]).intValue();
                } else {
                    this.frame[frameIndex++] =
                            Frame.UNINITIALIZED | this.cw.addUninitializedType("", ((Label) stack[i]).position);
                }
            }
            endFrame();
        } else {
            int delta;
            if (this.stackMap == null) {
                this.stackMap = new ByteVector();
                delta = this.code.length;
            } else {
                delta = this.code.length - this.previousFrameOffset - 1;
                if (delta < 0) {
                    if (type == Opcodes.F_SAME) {
                        return;
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }

            switch (type) {
                case Opcodes.F_FULL:
                    this.currentLocals = nLocal;
                    this.stackMap.putByte(FULL_FRAME).putShort(delta).putShort(nLocal);
                    for (int i = 0; i < nLocal; ++i) {
                        writeFrameType(local[i]);
                    }
                    this.stackMap.putShort(nStack);
                    for (int i = 0; i < nStack; ++i) {
                        writeFrameType(stack[i]);
                    }
                    break;
                case Opcodes.F_APPEND:
                    this.currentLocals += nLocal;
                    this.stackMap.putByte(SAME_FRAME_EXTENDED + nLocal).putShort(delta);
                    for (int i = 0; i < nLocal; ++i) {
                        writeFrameType(local[i]);
                    }
                    break;
                case Opcodes.F_CHOP:
                    this.currentLocals -= nLocal;
                    this.stackMap.putByte(SAME_FRAME_EXTENDED - nLocal).putShort(delta);
                    break;
                case Opcodes.F_SAME:
                    if (delta < 64) {
                        this.stackMap.putByte(delta);
                    } else {
                        this.stackMap.putByte(SAME_FRAME_EXTENDED).putShort(delta);
                    }
                    break;
                case Opcodes.F_SAME1:
                    if (delta < 64) {
                        this.stackMap.putByte(SAME_LOCALS_1_STACK_ITEM_FRAME + delta);
                    } else {
                        this.stackMap.putByte(SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED).putShort(delta);
                    }
                    writeFrameType(stack[0]);
                    break;
            }

            this.previousFrameOffset = this.code.length;
            ++this.frameCount;
        }

        this.maxStack = Math.max(this.maxStack, nStack);
        this.maxLocals = Math.max(this.maxLocals, this.currentLocals);
    }

    @Override
    public void visitInsn(final int opcode) {
        // adds the instruction to the bytecode of the method
        this.code.putByte(opcode);
        // update currentBlock
        // Label currentBlock = this.currentBlock;
        if (this.currentBlock != null) {
            if (this.compute == FRAMES) {
                this.currentBlock.frame.execute(opcode, 0, null, null);
            } else {
                // updates current and max stack sizes
                int size = this.stackSize + Frame.SIZE[opcode];
                if (size > this.maxStackSize) {
                    this.maxStackSize = size;
                }
                this.stackSize = size;
            }
            // if opcode == ATHROW or xRETURN, ends current block (no successor)
            if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW) {
                noSuccessor();
            }
        }
    }

    @Override
    public void visitIntInsn(final int opcode, final int operand) {
        // Label currentBlock = this.currentBlock;
        if (this.currentBlock != null) {
            if (this.compute == FRAMES) {
                this.currentBlock.frame.execute(opcode, operand, null, null);
            } else if (opcode != Opcodes.NEWARRAY) {
                // updates current and max stack sizes only for NEWARRAY
                // (stack size variation = 0 for BIPUSH or SIPUSH)
                int size = this.stackSize + 1;
                if (size > this.maxStackSize) {
                    this.maxStackSize = size;
                }
                this.stackSize = size;
            }
        }
        // adds the instruction to the bytecode of the method
        if (opcode == Opcodes.SIPUSH) {
            this.code.put12(opcode, operand);
        } else { // BIPUSH or NEWARRAY
            this.code.put11(opcode, operand);
        }
    }

    @Override
    public void visitVarInsn(final int opcode, final int var) {
        // Label currentBlock = this.currentBlock;
        if (this.currentBlock != null) {
            if (this.compute == FRAMES) {
                this.currentBlock.frame.execute(opcode, var, null, null);
            } else {
                // updates current and max stack sizes
                if (opcode == Opcodes.RET) {
                    // no stack change, but end of current block (no successor)
                    this.currentBlock.status |= Label.RET;
                    // save 'stackSize' here for future use
                    // (see {@link #findSubroutineSuccessors})
                    this.currentBlock.inputStackTop = this.stackSize;
                    noSuccessor();
                } else { // xLOAD or xSTORE
                    int size = this.stackSize + Frame.SIZE[opcode];
                    if (size > this.maxStackSize) {
                        this.maxStackSize = size;
                    }
                    this.stackSize = size;
                }
            }
        }
        if (this.compute != NOTHING) {
            // updates max locals
            int n;
            if (opcode == Opcodes.LLOAD || opcode == Opcodes.DLOAD || opcode == Opcodes.LSTORE
                    || opcode == Opcodes.DSTORE) {
                n = var + 2;
            } else {
                n = var + 1;
            }
            if (n > this.maxLocals) {
                this.maxLocals = n;
            }
        }
        // adds the instruction to the bytecode of the method
        if (var < 4 && opcode != Opcodes.RET) {
            int opt;
            if (opcode < Opcodes.ISTORE) {
                /* ILOAD_0 */
                opt = 26 + ((opcode - Opcodes.ILOAD) << 2) + var;
            } else {
                /* ISTORE_0 */
                opt = 59 + ((opcode - Opcodes.ISTORE) << 2) + var;
            }
            this.code.putByte(opt);
        } else if (var >= 256) {
            this.code.putByte(196 /* WIDE */).put12(opcode, var);
        } else {
            this.code.put11(opcode, var);
        }
        if (opcode >= Opcodes.ISTORE && this.compute == FRAMES && this.handlerCount > 0) {
            visitLabel(new Label());
        }
    }

    @Override
    public void visitTypeInsn(final int opcode, final String type) {
        Item i = this.cw.newClassItem(type);
        // Label currentBlock = this.currentBlock;
        if (this.currentBlock != null) {
            if (this.compute == FRAMES) {
                this.currentBlock.frame.execute(opcode, this.code.length, this.cw, i);
            } else if (opcode == Opcodes.NEW) {
                // updates current and max stack sizes only if opcode == NEW
                // (no stack change for ANEWARRAY, CHECKCAST, INSTANCEOF)
                int size = this.stackSize + 1;
                if (size > this.maxStackSize) {
                    this.maxStackSize = size;
                }
                this.stackSize = size;
            }
        }
        // adds the instruction to the bytecode of the method
        this.code.put12(opcode, i.index);
    }

    @Override
    public void visitFieldInsn(final int opcode, final String owner, final String name,
                               final String desc) {
        Item i = this.cw.newFieldItem(owner, name, desc);
        // Label currentBlock = this.currentBlock;
        if (this.currentBlock != null) {
            if (this.compute == FRAMES) {
                this.currentBlock.frame.execute(opcode, 0, this.cw, i);
            } else {
                int size;
                // computes the stack size variation
                char c = desc.charAt(0);
                switch (opcode) {
                    case Opcodes.GETSTATIC:
                        size = this.stackSize + (c == 'D' || c == 'J' ? 2 : 1);
                        break;
                    case Opcodes.PUTSTATIC:
                        size = this.stackSize + (c == 'D' || c == 'J' ? -2 : -1);
                        break;
                    case Opcodes.GETFIELD:
                        size = this.stackSize + (c == 'D' || c == 'J' ? 1 : 0);
                        break;
                    // case Constants.PUTFIELD:
                    default:
                        size = this.stackSize + (c == 'D' || c == 'J' ? -3 : -2);
                        break;
                }
                // updates current and max stack sizes
                if (size > this.maxStackSize) {
                    this.maxStackSize = size;
                }
                this.stackSize = size;
            }
        }
        // adds the instruction to the bytecode of the method
        this.code.put12(opcode, i.index);
    }

    @Override
    public void visitMethodInsn(final int opcode, final String owner, final String name,
                                final String desc) {
        boolean itf = opcode == Opcodes.INVOKEINTERFACE;
        Item i = this.cw.newMethodItem(owner, name, desc, itf);
        int argSize = i.intVal;
        // Label currentBlock = this.currentBlock;
        if (this.currentBlock != null) {
            if (this.compute == FRAMES) {
                this.currentBlock.frame.execute(opcode, 0, this.cw, i);
            } else {
                /*
                 * computes the stack size variation. In order not to recompute several times this variation
                 * for the same Item, we use the intVal field of this item to store this variation, once it
                 * has been computed. More precisely this intVal field stores the sizes of the arguments and
                 * of the return value corresponding to desc.
                 */
                if (argSize == 0) {
                    // the above sizes have not been computed yet,
                    // so we compute them...
                    argSize = Type.getArgumentsAndReturnSizes(desc);
                    // ... and we save them in order
                    // not to recompute them in the future
                    i.intVal = argSize;
                }
                int size;
                if (opcode == Opcodes.INVOKESTATIC) {
                    size = this.stackSize - (argSize >> 2) + (argSize & 0x03) + 1;
                } else {
                    size = this.stackSize - (argSize >> 2) + (argSize & 0x03);
                }
                // updates current and max stack sizes
                if (size > this.maxStackSize) {
                    this.maxStackSize = size;
                }
                this.stackSize = size;
            }
        }
        // adds the instruction to the bytecode of the method
        if (itf) {
            if (argSize == 0) {
                argSize = Type.getArgumentsAndReturnSizes(desc);
                i.intVal = argSize;
            }
            this.code.put12(Opcodes.INVOKEINTERFACE, i.index).put11(argSize >> 2, 0);
        } else {
            this.code.put12(opcode, i.index);
        }
    }

    @Override
    public void visitInvokeDynamicInsn(final String name, final String desc, final Handle bsm,
                                       final Object... bsmArgs) {
        Item i = this.cw.newInvokeDynamicItem(name, desc, bsm, bsmArgs);
        int argSize = i.intVal;
        // Label currentBlock = this.currentBlock;
        if (this.currentBlock != null) {
            if (this.compute == FRAMES) {
                this.currentBlock.frame.execute(Opcodes.INVOKEDYNAMIC, 0, this.cw, i);
            } else {
                /*
                 * computes the stack size variation. In order not to recompute several times this variation
                 * for the same Item, we use the intVal field of this item to store this variation, once it
                 * has been computed. More precisely this intVal field stores the sizes of the arguments and
                 * of the return value corresponding to desc.
                 */
                if (argSize == 0) {
                    // the above sizes have not been computed yet,
                    // so we compute them...
                    argSize = Type.getArgumentsAndReturnSizes(desc);
                    // ... and we save them in order
                    // not to recompute them in the future
                    i.intVal = argSize;
                }
                int size = this.stackSize - (argSize >> 2) + (argSize & 0x03) + 1;

                // updates current and max stack sizes
                if (size > this.maxStackSize) {
                    this.maxStackSize = size;
                }
                this.stackSize = size;
            }
        }
        // adds the instruction to the bytecode of the method
        this.code.put12(Opcodes.INVOKEDYNAMIC, i.index);
        this.code.putShort(0);
    }

    @Override
    public void visitJumpInsn(final int opcode, final Label label) {
        Label nextInsn = null;
        // Label currentBlock = this.currentBlock;
        if (this.currentBlock != null) {
            if (this.compute == FRAMES) {
                this.currentBlock.frame.execute(opcode, 0, null, null);
                // 'label' is the target of a jump instruction
                label.getFirst().status |= Label.TARGET;
                // adds 'label' as a successor of this basic block
                addSuccessor(Edge.NORMAL, label);
                if (opcode != Opcodes.GOTO) {
                    // creates a Label for the next basic block
                    nextInsn = new Label();
                }
            } else {
                if (opcode == Opcodes.JSR) {
                    if ((label.status & Label.SUBROUTINE) == 0) {
                        label.status |= Label.SUBROUTINE;
                        ++this.subroutines;
                    }
                    this.currentBlock.status |= Label.JSR;
                    addSuccessor(this.stackSize + 1, label);
                    // creates a Label for the next basic block
                    nextInsn = new Label();
                    /*
                     * note that, by construction in this method, a JSR block has at least two successors in
                     * the control flow graph: the first one leads the next instruction after the JSR, while
                     * the second one leads to the JSR target.
                     */
                } else {
                    // updates current stack size (max stack size unchanged
                    // because stack size variation always negative in this
                    // case)
                    this.stackSize += Frame.SIZE[opcode];
                    addSuccessor(this.stackSize, label);
                }
            }
        }
        // adds the instruction to the bytecode of the method
        if ((label.status & Label.RESOLVED) != 0
                && label.position - this.code.length < Short.MIN_VALUE) {
            /*
             * case of a backward jump with an offset < -32768. In this case we automatically replace GOTO
             * with GOTO_W, JSR with JSR_W and IFxxx <l> with IFNOTxxx <l'> GOTO_W <l>, where IFNOTxxx is
             * the "opposite" opcode of IFxxx (i.e., IFNE for IFEQ) and where <l'> designates the
             * instruction just after the GOTO_W.
             */
            if (opcode == Opcodes.GOTO) {
                this.code.putByte(200); // GOTO_W
            } else if (opcode == Opcodes.JSR) {
                this.code.putByte(201); // JSR_W
            } else {
                // if the IF instruction is transformed into IFNOT GOTO_W the
                // next instruction becomes the target of the IFNOT instruction
                if (nextInsn != null) {
                    nextInsn.status |= Label.TARGET;
                }
                this.code.putByte(opcode <= 166 ? ((opcode + 1) ^ 1) - 1 : opcode ^ 1);
                this.code.putShort(8); // jump offset
                this.code.putByte(200); // GOTO_W
            }
            label.put(this, this.code, this.code.length - 1, true);
        } else {
            /*
             * case of a backward jump with an offset >= -32768, or of a forward jump with, of course, an
             * unknown offset. In these cases we store the offset in 2 bytes (which will be increased in
             * resizeInstructions, if needed).
             */
            this.code.putByte(opcode);
            label.put(this, this.code, this.code.length - 1, false);
        }
        if (this.currentBlock != null) {
            if (nextInsn != null) {
                // if the jump instruction is not a GOTO, the next instruction
                // is also a successor of this instruction. Calling visitLabel
                // adds the label of this next instruction as a successor of the
                // current block, and starts a new basic block
                visitLabel(nextInsn);
            }
            if (opcode == Opcodes.GOTO) {
                noSuccessor();
            }
        }
    }

    @Override
    public void visitLabel(final Label label) {
        // resolves previous forward references to label, if any
        this.resize |= label.resolve(this, this.code.length, this.code.data);
        // updates currentBlock
        if ((label.status & Label.DEBUG) != 0) {
            return;
        }
        if (this.compute == FRAMES) {
            if (this.currentBlock != null) {
                if (label.position == this.currentBlock.position) {
                    // successive labels, do not start a new basic block
                    this.currentBlock.status |= (label.status & Label.TARGET);
                    label.frame = this.currentBlock.frame;
                    return;
                }
                // ends current block (with one new successor)
                addSuccessor(Edge.NORMAL, label);
            }
            // begins a new current block
            this.currentBlock = label;
            if (label.frame == null) {
                label.frame = new Frame();
                label.frame.owner = label;
            }
            // updates the basic block list
            if (this.previousBlock != null) {
                if (label.position == this.previousBlock.position) {
                    this.previousBlock.status |= (label.status & Label.TARGET);
                    label.frame = this.previousBlock.frame;
                    this.currentBlock = this.previousBlock;
                    return;
                }
                this.previousBlock.successor = label;
            }
            this.previousBlock = label;
        } else if (this.compute == MAXS) {
            if (this.currentBlock != null) {
                // ends current block (with one new successor)
                this.currentBlock.outputStackMax = this.maxStackSize;
                addSuccessor(this.stackSize, label);
            }
            // begins a new current block
            this.currentBlock = label;
            // resets the relative current and max stack sizes
            this.stackSize = 0;
            this.maxStackSize = 0;
            // updates the basic block list
            if (this.previousBlock != null) {
                this.previousBlock.successor = label;
            }
            this.previousBlock = label;
        }
    }

    @Override
    public void visitLdcInsn(final Object cst) {
        Item i = this.cw.newConstItem(cst);
        // Label currentBlock = this.currentBlock;
        if (this.currentBlock != null) {
            if (this.compute == FRAMES) {
                this.currentBlock.frame.execute(Opcodes.LDC, 0, this.cw, i);
            } else {
                int size;
                // computes the stack size variation
                if (i.type == ClassWriter.LONG || i.type == ClassWriter.DOUBLE) {
                    size = this.stackSize + 2;
                } else {
                    size = this.stackSize + 1;
                }
                // updates current and max stack sizes
                if (size > this.maxStackSize) {
                    this.maxStackSize = size;
                }
                this.stackSize = size;
            }
        }
        // adds the instruction to the bytecode of the method
        int index = i.index;
        if (i.type == ClassWriter.LONG || i.type == ClassWriter.DOUBLE) {
            this.code.put12(20 /* LDC2_W */, index);
        } else if (index >= 256) {
            this.code.put12(19 /* LDC_W */, index);
        } else {
            this.code.put11(Opcodes.LDC, index);
        }
    }

    @Override
    public void visitIincInsn(final int var, final int increment) {
        if (this.currentBlock != null) {
            if (this.compute == FRAMES) {
                this.currentBlock.frame.execute(Opcodes.IINC, var, null, null);
            }
        }
        if (this.compute != NOTHING) {
            // updates max locals
            int n = var + 1;
            if (n > this.maxLocals) {
                this.maxLocals = n;
            }
        }
        // adds the instruction to the bytecode of the method
        if ((var > 255) || (increment > 127) || (increment < -128)) {
            this.code.putByte(196 /* WIDE */).put12(Opcodes.IINC, var).putShort(increment);
        } else {
            this.code.putByte(Opcodes.IINC).put11(var, increment);
        }
    }

    @Override
    public void visitTableSwitchInsn(final int min, final int max, final Label dflt,
                                     final Label... labels) {
        // adds the instruction to the bytecode of the method
        int source = this.code.length;
        this.code.putByte(Opcodes.TABLESWITCH);
        this.code.putByteArray(null, 0, (4 - this.code.length % 4) % 4);
        dflt.put(this, this.code, source, true);
        this.code.putInt(min).putInt(max);
        for (int i = 0; i < labels.length; ++i) {
            labels[i].put(this, this.code, source, true);
        }
        // updates currentBlock
        visitSwitchInsn(dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(final Label dflt, final int[] keys, final Label[] labels) {
        // adds the instruction to the bytecode of the method
        int source = this.code.length;
        this.code.putByte(Opcodes.LOOKUPSWITCH);
        this.code.putByteArray(null, 0, (4 - this.code.length % 4) % 4);
        dflt.put(this, this.code, source, true);
        this.code.putInt(labels.length);
        for (int i = 0; i < labels.length; ++i) {
            this.code.putInt(keys[i]);
            labels[i].put(this, this.code, source, true);
        }
        // updates currentBlock
        visitSwitchInsn(dflt, labels);
    }

    private void visitSwitchInsn(final Label dflt, final Label[] labels) {
        // Label currentBlock = this.currentBlock;
        if (this.currentBlock != null) {
            if (this.compute == FRAMES) {
                this.currentBlock.frame.execute(Opcodes.LOOKUPSWITCH, 0, null, null);
                // adds current block successors
                addSuccessor(Edge.NORMAL, dflt);
                dflt.getFirst().status |= Label.TARGET;
                for (int i = 0; i < labels.length; ++i) {
                    addSuccessor(Edge.NORMAL, labels[i]);
                    labels[i].getFirst().status |= Label.TARGET;
                }
            } else {
                // updates current stack size (max stack size unchanged)
                --this.stackSize;
                // adds current block successors
                addSuccessor(this.stackSize, dflt);
                for (int i = 0; i < labels.length; ++i) {
                    addSuccessor(this.stackSize, labels[i]);
                }
            }
            // ends current block
            noSuccessor();
        }
    }

    @Override
    public void visitMultiANewArrayInsn(final String desc, final int dims) {
        Item i = this.cw.newClassItem(desc);
        // Label currentBlock = this.currentBlock;
        if (this.currentBlock != null) {
            if (this.compute == FRAMES) {
                this.currentBlock.frame.execute(Opcodes.MULTIANEWARRAY, dims, this.cw, i);
            } else {
                // updates current stack size (max stack size unchanged because
                // stack size variation always negative or null)
                this.stackSize += 1 - dims;
            }
        }
        // adds the instruction to the bytecode of the method
        this.code.put12(Opcodes.MULTIANEWARRAY, i.index).putByte(dims);
    }

    @Override
    public void visitTryCatchBlock(final Label start, final Label end, final Label handler,
                                   final String type) {
        ++this.handlerCount;
        Handler h = new Handler();
        h.start = start;
        h.end = end;
        h.handler = handler;
        h.desc = type;
        h.type = type != null ? this.cw.newClass(type) : 0;
        if (this.lastHandler == null) {
            this.firstHandler = h;
        } else {
            this.lastHandler.next = h;
        }
        this.lastHandler = h;
    }

    @Override
    public void visitLocalVariable(final String name, final String desc, final String signature,
                                   final Label start, final Label end, final int index) {
        if (signature != null) {
            if (this.localVarType == null) {
                this.localVarType = new ByteVector();
            }
            ++this.localVarTypeCount;
            this.localVarType.putShort(start.position).putShort(end.position - start.position)
                    .putShort(this.cw.newUTF8(name)).putShort(this.cw.newUTF8(signature)).putShort(index);
        }
        if (this.localVar == null) {
            this.localVar = new ByteVector();
        }
        ++this.localVarCount;
        this.localVar.putShort(start.position).putShort(end.position - start.position)
                .putShort(this.cw.newUTF8(name)).putShort(this.cw.newUTF8(desc)).putShort(index);
        if (this.compute != NOTHING) {
            // updates max locals
            char c = desc.charAt(0);
            int n = index + (c == 'J' || c == 'D' ? 2 : 1);
            if (n > this.maxLocals) {
                this.maxLocals = n;
            }
        }
    }

    @Override
    public void visitLineNumber(final int line, final Label start) {
        if (this.lineNumber == null) {
            this.lineNumber = new ByteVector();
        }
        ++this.lineNumberCount;
        this.lineNumber.putShort(start.position);
        this.lineNumber.putShort(line);
    }

    @Override
    public void visitMaxs(final int maxStack, final int maxLocals) {
        if (ClassReader.FRAMES && this.compute == FRAMES) {
            // completes the control flow graph with exception handler blocks
            Handler handler = this.firstHandler;
            while (handler != null) {
                Label l = handler.start.getFirst();
                Label h = handler.handler.getFirst();
                Label e = handler.end.getFirst();
                // computes the kind of the edges to 'h'
                String t = handler.desc == null ? "java/lang/Throwable" : handler.desc;
                int kind = Frame.OBJECT | this.cw.addType(t);
                // h is an exception handler
                h.status |= Label.TARGET;
                // adds 'h' as a successor of labels between 'start' and 'end'
                while (l != e) {
                    // creates an edge to 'h'
                    Edge b = new Edge();
                    b.info = kind;
                    b.successor = h;
                    // adds it to the successors of 'l'
                    b.next = l.successors;
                    l.successors = b;
                    // goes to the next label
                    l = l.successor;
                }
                handler = handler.next;
            }

            // creates and visits the first (implicit) frame
            Frame f = this.labels.frame;
            Type[] args = Type.getArgumentTypes(this.descriptor);
            f.initInputFrame(this.cw, this.access, args, this.maxLocals);
            visitFrame(f);

            /*
             * fix point algorithm: mark the first basic block as 'changed' (i.e. put it in the 'changed'
             * list) and, while there are changed basic blocks, choose one, mark it as unchanged, and
             * update its successors (which can be changed in the process).
             */
            int max = 0;
            Label changed = this.labels;
            while (changed != null) {
                // removes a basic block from the list of changed basic blocks
                Label l = changed;
                changed = changed.next;
                l.next = null;
                f = l.frame;
                // a reachable jump target must be stored in the stack map
                if ((l.status & Label.TARGET) != 0) {
                    l.status |= Label.STORE;
                }
                // all visited labels are reachable, by definition
                l.status |= Label.REACHABLE;
                // updates the (absolute) maximum stack size
                int blockMax = f.inputStack.length + l.outputStackMax;
                if (blockMax > max) {
                    max = blockMax;
                }
                // updates the successors of the current basic block
                Edge e = l.successors;
                while (e != null) {
                    Label n = e.successor.getFirst();
                    boolean change = f.merge(this.cw, n.frame, e.info);
                    if (change && n.next == null) {
                        // if n has changed and is not already in the 'changed'
                        // list, adds it to this list
                        n.next = changed;
                        changed = n;
                    }
                    e = e.next;
                }
            }

            // visits all the frames that must be stored in the stack map
            Label l = this.labels;
            while (l != null) {
                f = l.frame;
                if ((l.status & Label.STORE) != 0) {
                    visitFrame(f);
                }
                if ((l.status & Label.REACHABLE) == 0) {
                    // finds start and end of dead basic block
                    Label k = l.successor;
                    int start = l.position;
                    int end = (k == null ? this.code.length : k.position) - 1;
                    // if non empty basic block
                    if (end >= start) {
                        max = Math.max(max, 1);
                        // replaces instructions with NOP ... NOP ATHROW
                        for (int i = start; i < end; ++i) {
                            this.code.data[i] = Opcodes.NOP;
                        }
                        this.code.data[end] = (byte) Opcodes.ATHROW;
                        // emits a frame for this unreachable block
                        int frameIndex = startFrame(start, 0, 1);
                        this.frame[frameIndex] = Frame.OBJECT | this.cw.addType("java/lang/Throwable");
                        endFrame();
                        // removes the start-end range from the exception
                        // handlers
                        this.firstHandler = Handler.remove(this.firstHandler, l, k);
                    }
                }
                l = l.successor;
            }

            handler = this.firstHandler;
            this.handlerCount = 0;
            while (handler != null) {
                this.handlerCount += 1;
                handler = handler.next;
            }

            this.maxStack = max;
        } else if (this.compute == MAXS) {
            // completes the control flow graph with exception handler blocks
            Handler handler = this.firstHandler;
            while (handler != null) {
                Label l = handler.start;
                Label h = handler.handler;
                Label e = handler.end;
                // adds 'h' as a successor of labels between 'start' and 'end'
                while (l != e) {
                    // creates an edge to 'h'
                    Edge b = new Edge();
                    b.info = Edge.EXCEPTION;
                    b.successor = h;
                    // adds it to the successors of 'l'
                    if ((l.status & Label.JSR) == 0) {
                        b.next = l.successors;
                        l.successors = b;
                    } else {
                        // if l is a JSR block, adds b after the first two edges
                        // to preserve the hypothesis about JSR block successors
                        // order (see {@link #visitJumpInsn})
                        b.next = l.successors.next.next;
                        l.successors.next.next = b;
                    }
                    // goes to the next label
                    l = l.successor;
                }
                handler = handler.next;
            }

            if (this.subroutines > 0) {
                // completes the control flow graph with the RET successors
                /*
                 * first step: finds the subroutines. This step determines, for each basic block, to which
                 * subroutine(s) it belongs.
                 */
                // finds the basic blocks that belong to the "main" subroutine
                int id = 0;
                this.labels.visitSubroutine(null, 1, this.subroutines);
                // finds the basic blocks that belong to the real subroutines
                Label l = this.labels;
                while (l != null) {
                    if ((l.status & Label.JSR) != 0) {
                        // the subroutine is defined by l's TARGET, not by l
                        Label subroutine = l.successors.next.successor;
                        // if this subroutine has not been visited yet...
                        if ((subroutine.status & Label.VISITED) == 0) {
                            // ...assigns it a new id and finds its basic blocks
                            id += 1;
                            subroutine.visitSubroutine(null, (id / 32L) << 32 | (1L << (id % 32)),
                                    this.subroutines);
                        }
                    }
                    l = l.successor;
                }
                // second step: finds the successors of RET blocks
                l = this.labels;
                while (l != null) {
                    if ((l.status & Label.JSR) != 0) {
                        Label L = this.labels;
                        while (L != null) {
                            L.status &= ~Label.VISITED2;
                            L = L.successor;
                        }
                        // the subroutine is defined by l's TARGET, not by l
                        Label subroutine = l.successors.next.successor;
                        subroutine.visitSubroutine(l, 0, this.subroutines);
                    }
                    l = l.successor;
                }
            }

            /*
             * control flow analysis algorithm: while the block stack is not empty, pop a block from this
             * stack, update the max stack size, compute the true (non relative) begin stack size of the
             * successors of this block, and push these successors onto the stack (unless they have
             * already been pushed onto the stack). Note: by hypothesis, the {@link Label#inputStackTop}
             * of the blocks in the block stack are the true (non relative) beginning stack sizes of these
             * blocks.
             */
            int max = 0;
            Label stack = this.labels;
            while (stack != null) {
                // pops a block from the stack
                Label l = stack;
                stack = stack.next;
                // computes the true (non relative) max stack size of this block
                int start = l.inputStackTop;
                int blockMax = start + l.outputStackMax;
                // updates the global max stack size
                if (blockMax > max) {
                    max = blockMax;
                }
                // analyzes the successors of the block
                Edge b = l.successors;
                if ((l.status & Label.JSR) != 0) {
                    // ignores the first edge of JSR blocks (virtual successor)
                    b = b.next;
                }
                while (b != null) {
                    l = b.successor;
                    // if this successor has not already been pushed...
                    if ((l.status & Label.PUSHED) == 0) {
                        // computes its true beginning stack size...
                        l.inputStackTop = b.info == Edge.EXCEPTION ? 1 : start + b.info;
                        // ...and pushes it onto the stack
                        l.status |= Label.PUSHED;
                        l.next = stack;
                        stack = l;
                    }
                    b = b.next;
                }
            }
            this.maxStack = Math.max(maxStack, max);
        } else {
            this.maxStack = maxStack;
            this.maxLocals = maxLocals;
        }
    }

    @Override
    public void visitEnd() {
    }

    // ------------------------------------------------------------------------
    // Utility methods: control flow analysis algorithm
    // ------------------------------------------------------------------------

    /**
     * Adds a successor to the {@link #currentBlock currentBlock} block.
     *
     * @param info      information about the control flow edge to be added.
     * @param successor the successor block to be added to the current block.
     */
    private void addSuccessor(final int info, final Label successor) {
        // creates and initializes an Edge object...
        Edge b = new Edge();
        b.info = info;
        b.successor = successor;
        // ...and adds it to the successor list of the currentBlock block
        b.next = this.currentBlock.successors;
        this.currentBlock.successors = b;
    }

    /**
     * Ends the current basic block. This method must be used in the case where the current basic
     * block does not have any successor.
     */
    private void noSuccessor() {
        if (this.compute == FRAMES) {
            Label l = new Label();
            l.frame = new Frame();
            l.frame.owner = l;
            l.resolve(this, this.code.length, this.code.data);
            this.previousBlock.successor = l;
            this.previousBlock = l;
        } else {
            this.currentBlock.outputStackMax = this.maxStackSize;
        }
        this.currentBlock = null;
    }

    // ------------------------------------------------------------------------
    // Utility methods: stack map frames
    // ------------------------------------------------------------------------

    /**
     * Visits a frame that has been computed from scratch.
     *
     * @param f the frame that must be visited.
     */
    private void visitFrame(final Frame f) {
        int i, t;
        int nTop = 0;
        int nLocal = 0;
        int nStack = 0;
        int[] locals = f.inputLocals;
        int[] stacks = f.inputStack;
        // computes the number of locals (ignores TOP types that are just after
        // a LONG or a DOUBLE, and all trailing TOP types)
        for (i = 0; i < locals.length; ++i) {
            t = locals[i];
            if (t == Frame.TOP) {
                ++nTop;
            } else {
                nLocal += nTop + 1;
                nTop = 0;
            }
            if (t == Frame.LONG || t == Frame.DOUBLE) {
                ++i;
            }
        }
        // computes the stack size (ignores TOP types that are just after
        // a LONG or a DOUBLE)
        for (i = 0; i < stacks.length; ++i) {
            t = stacks[i];
            ++nStack;
            if (t == Frame.LONG || t == Frame.DOUBLE) {
                ++i;
            }
        }
        // visits the frame and its content
        int frameIndex = startFrame(f.owner.position, nLocal, nStack);
        for (i = 0; nLocal > 0; ++i, --nLocal) {
            t = locals[i];
            this.frame[frameIndex++] = t;
            if (t == Frame.LONG || t == Frame.DOUBLE) {
                ++i;
            }
        }
        for (i = 0; i < stacks.length; ++i) {
            t = stacks[i];
            this.frame[frameIndex++] = t;
            if (t == Frame.LONG || t == Frame.DOUBLE) {
                ++i;
            }
        }
        endFrame();
    }

    /**
     * Visit the implicit first frame of this method.
     */
    private void visitImplicitFirstFrame() {
        // There can be at most descriptor.length() + 1 locals
        int frameIndex = startFrame(0, this.descriptor.length() + 1, 0);
        if ((this.access & Opcodes.ACC_STATIC) == 0) {
            if ((this.access & ACC_CONSTRUCTOR) == 0) {
                this.frame[frameIndex++] = Frame.OBJECT | this.cw.addType(this.cw.thisName);
            } else {
                this.frame[frameIndex++] = 6; // Opcodes.UNINITIALIZED_THIS;
            }
        }
        int i = 1;
        loop:
        while (true) {
            int j = i;
            switch (this.descriptor.charAt(i++)) {
                case 'Z':
                case 'C':
                case 'B':
                case 'S':
                case 'I':
                    this.frame[frameIndex++] = 1; // Opcodes.INTEGER;
                    break;
                case 'F':
                    this.frame[frameIndex++] = 2; // Opcodes.FLOAT;
                    break;
                case 'J':
                    this.frame[frameIndex++] = 4; // Opcodes.LONG;
                    break;
                case 'D':
                    this.frame[frameIndex++] = 3; // Opcodes.DOUBLE;
                    break;
                case '[':
                    while (this.descriptor.charAt(i) == '[') {
                        ++i;
                    }
                    if (this.descriptor.charAt(i) == 'L') {
                        ++i;
                        while (this.descriptor.charAt(i) != ';') {
                            ++i;
                        }
                    }
                    this.frame[frameIndex++] =
                            Frame.OBJECT | this.cw.addType(this.descriptor.substring(j, ++i));
                    break;
                case 'L':
                    while (this.descriptor.charAt(i) != ';') {
                        ++i;
                    }
                    this.frame[frameIndex++] =
                            Frame.OBJECT | this.cw.addType(this.descriptor.substring(j + 1, i++));
                    break;
                default:
                    break loop;
            }
        }
        this.frame[1] = frameIndex - 3;
        endFrame();
    }

    /**
     * Starts the visit of a stack map frame.
     *
     * @param offset the offset of the instruction to which the frame corresponds.
     * @param nLocal the number of local variables in the frame.
     * @param nStack the number of stack elements in the frame.
     * @return the index of the next element to be written in this frame.
     */
    private int startFrame(final int offset, final int nLocal, final int nStack) {
        int n = 3 + nLocal + nStack;
        if (this.frame == null || this.frame.length < n) {
            this.frame = new int[n];
        }
        this.frame[0] = offset;
        this.frame[1] = nLocal;
        this.frame[2] = nStack;
        return 3;
    }

    /**
     * Checks if the visit of the current frame {@link #frame} is finished, and if yes, write it in
     * the StackMapTable attribute.
     */
    private void endFrame() {
        if (this.previousFrame != null) { // do not write the first frame
            if (this.stackMap == null) {
                this.stackMap = new ByteVector();
            }
            writeFrame();
            ++this.frameCount;
        }
        this.previousFrame = this.frame;
        this.frame = null;
    }

    /**
     * Compress and writes the current frame {@link #frame} in the StackMapTable attribute.
     */
    private void writeFrame() {
        int clocalsSize = this.frame[1];
        int cstackSize = this.frame[2];
        if ((this.cw.version & 0xFFFF) < Opcodes.V1_6) {
            this.stackMap.putShort(this.frame[0]).putShort(clocalsSize);
            writeFrameTypes(3, 3 + clocalsSize);
            this.stackMap.putShort(cstackSize);
            writeFrameTypes(3 + clocalsSize, 3 + clocalsSize + cstackSize);
            return;
        }
        int localsSize = this.previousFrame[1];
        int type = FULL_FRAME;
        int k = 0;
        int delta;
        if (this.frameCount == 0) {
            delta = this.frame[0];
        } else {
            delta = this.frame[0] - this.previousFrame[0] - 1;
        }
        if (cstackSize == 0) {
            k = clocalsSize - localsSize;
            switch (k) {
                case -3:
                case -2:
                case -1:
                    type = CHOP_FRAME;
                    localsSize = clocalsSize;
                    break;
                case 0:
                    type = delta < 64 ? SAME_FRAME : SAME_FRAME_EXTENDED;
                    break;
                case 1:
                case 2:
                case 3:
                    type = APPEND_FRAME;
                    break;
            }
        } else if (clocalsSize == localsSize && cstackSize == 1) {
            type = delta < 63 ? SAME_LOCALS_1_STACK_ITEM_FRAME : SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED;
        }
        if (type != FULL_FRAME) {
            // verify if locals are the same
            int l = 3;
            for (int j = 0; j < localsSize; j++) {
                if (this.frame[l] != this.previousFrame[l]) {
                    type = FULL_FRAME;
                    break;
                }
                l++;
            }
        }
        switch (type) {
            case SAME_FRAME:
                this.stackMap.putByte(delta);
                break;
            case SAME_LOCALS_1_STACK_ITEM_FRAME:
                this.stackMap.putByte(SAME_LOCALS_1_STACK_ITEM_FRAME + delta);
                writeFrameTypes(3 + clocalsSize, 4 + clocalsSize);
                break;
            case SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED:
                this.stackMap.putByte(SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED).putShort(delta);
                writeFrameTypes(3 + clocalsSize, 4 + clocalsSize);
                break;
            case SAME_FRAME_EXTENDED:
                this.stackMap.putByte(SAME_FRAME_EXTENDED).putShort(delta);
                break;
            case CHOP_FRAME:
                this.stackMap.putByte(SAME_FRAME_EXTENDED + k).putShort(delta);
                break;
            case APPEND_FRAME:
                this.stackMap.putByte(SAME_FRAME_EXTENDED + k).putShort(delta);
                writeFrameTypes(3 + localsSize, 3 + clocalsSize);
                break;
            // case FULL_FRAME:
            default:
                this.stackMap.putByte(FULL_FRAME).putShort(delta).putShort(clocalsSize);
                writeFrameTypes(3, 3 + clocalsSize);
                this.stackMap.putShort(cstackSize);
                writeFrameTypes(3 + clocalsSize, 3 + clocalsSize + cstackSize);
        }
    }

    /**
     * Writes some types of the current frame {@link #frame} into the StackMapTableAttribute. This
     * method converts types from the format used in {@link Label} to the format used in StackMapTable
     * attributes. In particular, it converts type table indexes to constant pool indexes.
     *
     * @param start index of the first type in {@link #frame} to write.
     * @param end   index of last type in {@link #frame} to write (exclusive).
     */
    private void writeFrameTypes(final int start, final int end) {
        for (int i = start; i < end; ++i) {
            int t = this.frame[i];
            int d = t & Frame.DIM;
            if (d == 0) {
                int v = t & Frame.BASE_VALUE;
                switch (t & Frame.BASE_KIND) {
                    case Frame.OBJECT:
                        this.stackMap.putByte(7).putShort(this.cw.newClass(this.cw.typeTable[v].strVal1));
                        break;
                    case Frame.UNINITIALIZED:
                        this.stackMap.putByte(8).putShort(this.cw.typeTable[v].intVal);
                        break;
                    default:
                        this.stackMap.putByte(v);
                }
            } else {
                StringBuilder buf = new StringBuilder();
                d >>= 28;
                while (d-- > 0) {
                    buf.append('[');
                }
                if ((t & Frame.BASE_KIND) == Frame.OBJECT) {
                    buf.append('L');
                    buf.append(this.cw.typeTable[t & Frame.BASE_VALUE].strVal1);
                    buf.append(';');
                } else {
                    switch (t & 0xF) {
                        case 1:
                            buf.append('I');
                            break;
                        case 2:
                            buf.append('F');
                            break;
                        case 3:
                            buf.append('D');
                            break;
                        case 9:
                            buf.append('Z');
                            break;
                        case 10:
                            buf.append('B');
                            break;
                        case 11:
                            buf.append('C');
                            break;
                        case 12:
                            buf.append('S');
                            break;
                        default:
                            buf.append('J');
                    }
                }
                this.stackMap.putByte(7).putShort(this.cw.newClass(buf.toString()));
            }
        }
    }

    private void writeFrameType(final Object type) {
        if (type instanceof String) {
            this.stackMap.putByte(7).putShort(this.cw.newClass((String) type));
        } else if (type instanceof Integer) {
            this.stackMap.putByte(((Integer) type).intValue());
        } else {
            this.stackMap.putByte(8).putShort(((Label) type).position);
        }
    }

    // ------------------------------------------------------------------------
    // Utility methods: dump bytecode array
    // ------------------------------------------------------------------------

    /**
     * Returns the size of the bytecode of this method.
     *
     * @return the size of the bytecode of this method.
     */
    final int getSize() {
        if (this.classReaderOffset != 0) {
            return 6 + this.classReaderLength;
        }
        if (this.resize) {
            // replaces the temporary jump opcodes introduced by Label.resolve.
            if (ClassReader.RESIZE) {
                resizeInstructions();
            } else {
                throw new RuntimeException("Method codegen too large!");
            }
        }
        int size = 8;
        if (this.code.length > 0) {
            if (this.code.length > 65536) {
                throw new RuntimeException("Method codegen too large!");
            }
            this.cw.newUTF8("Code");
            size += 18 + this.code.length + 8 * this.handlerCount;
            if (this.localVar != null) {
                this.cw.newUTF8("LocalVariableTable");
                size += 8 + this.localVar.length;
            }
            if (this.localVarType != null) {
                this.cw.newUTF8("LocalVariableTypeTable");
                size += 8 + this.localVarType.length;
            }
            if (this.lineNumber != null) {
                this.cw.newUTF8("LineNumberTable");
                size += 8 + this.lineNumber.length;
            }
            if (this.stackMap != null) {
                boolean zip = (this.cw.version & 0xFFFF) >= Opcodes.V1_6;
                this.cw.newUTF8(zip ? "StackMapTable" : "StackMap");
                size += 8 + this.stackMap.length;
            }
            if (this.cattrs != null) {
                size += this.cattrs.getSize(this.cw, this.code.data, this.code.length, this.maxStack,
                        this.maxLocals);
            }
        }
        if (this.exceptionCount > 0) {
            this.cw.newUTF8("Exceptions");
            size += 8 + 2 * this.exceptionCount;
        }
        if ((this.access & Opcodes.ACC_SYNTHETIC) != 0) {
            if ((this.cw.version & 0xFFFF) < Opcodes.V1_5
                    || (this.access & ClassWriter.ACC_SYNTHETIC_ATTRIBUTE) != 0) {
                this.cw.newUTF8("Synthetic");
                size += 6;
            }
        }
        if ((this.access & Opcodes.ACC_DEPRECATED) != 0) {
            this.cw.newUTF8("Deprecated");
            size += 6;
        }
        if (ClassReader.SIGNATURES && this.signature != null) {
            this.cw.newUTF8("Signature");
            this.cw.newUTF8(this.signature);
            size += 8;
        }
        if (ClassReader.ANNOTATIONS && this.annd != null) {
            this.cw.newUTF8("AnnotationDefault");
            size += 6 + this.annd.length;
        }
        if (ClassReader.ANNOTATIONS && this.anns != null) {
            this.cw.newUTF8("RuntimeVisibleAnnotations");
            size += 8 + this.anns.getSize();
        }
        if (ClassReader.ANNOTATIONS && this.ianns != null) {
            this.cw.newUTF8("RuntimeInvisibleAnnotations");
            size += 8 + this.ianns.getSize();
        }
        if (ClassReader.ANNOTATIONS && this.panns != null) {
            this.cw.newUTF8("RuntimeVisibleParameterAnnotations");
            size += 7 + 2 * (this.panns.length - this.synthetics);
            for (int i = this.panns.length - 1; i >= this.synthetics; --i) {
                size += this.panns[i] == null ? 0 : this.panns[i].getSize();
            }
        }
        if (ClassReader.ANNOTATIONS && this.ipanns != null) {
            this.cw.newUTF8("RuntimeInvisibleParameterAnnotations");
            size += 7 + 2 * (this.ipanns.length - this.synthetics);
            for (int i = this.ipanns.length - 1; i >= this.synthetics; --i) {
                size += this.ipanns[i] == null ? 0 : this.ipanns[i].getSize();
            }
        }
        if (this.attrs != null) {
            size += this.attrs.getSize(this.cw, null, 0, -1, -1);
        }
        return size;
    }

    /**
     * Puts the bytecode of this method in the given byte vector.
     *
     * @param out the byte vector into which the bytecode of this method must be copied.
     */
    final void put(final ByteVector out) {
        final int FACTOR = ClassWriter.TO_ACC_SYNTHETIC;
        int mask = ACC_CONSTRUCTOR | Opcodes.ACC_DEPRECATED | ClassWriter.ACC_SYNTHETIC_ATTRIBUTE
                | ((this.access & ClassWriter.ACC_SYNTHETIC_ATTRIBUTE) / FACTOR);
        out.putShort(this.access & ~mask).putShort(this.name).putShort(this.desc);
        if (this.classReaderOffset != 0) {
            out.putByteArray(this.cw.cr.b, this.classReaderOffset, this.classReaderLength);
            return;
        }
        int attributeCount = 0;
        if (this.code.length > 0) {
            ++attributeCount;
        }
        if (this.exceptionCount > 0) {
            ++attributeCount;
        }
        if ((this.access & Opcodes.ACC_SYNTHETIC) != 0) {
            if ((this.cw.version & 0xFFFF) < Opcodes.V1_5
                    || (this.access & ClassWriter.ACC_SYNTHETIC_ATTRIBUTE) != 0) {
                ++attributeCount;
            }
        }
        if ((this.access & Opcodes.ACC_DEPRECATED) != 0) {
            ++attributeCount;
        }
        if (ClassReader.SIGNATURES && this.signature != null) {
            ++attributeCount;
        }
        if (ClassReader.ANNOTATIONS && this.annd != null) {
            ++attributeCount;
        }
        if (ClassReader.ANNOTATIONS && this.anns != null) {
            ++attributeCount;
        }
        if (ClassReader.ANNOTATIONS && this.ianns != null) {
            ++attributeCount;
        }
        if (ClassReader.ANNOTATIONS && this.panns != null) {
            ++attributeCount;
        }
        if (ClassReader.ANNOTATIONS && this.ipanns != null) {
            ++attributeCount;
        }
        if (this.attrs != null) {
            attributeCount += this.attrs.getCount();
        }
        out.putShort(attributeCount);
        if (this.code.length > 0) {
            int size = 12 + this.code.length + 8 * this.handlerCount;
            if (this.localVar != null) {
                size += 8 + this.localVar.length;
            }
            if (this.localVarType != null) {
                size += 8 + this.localVarType.length;
            }
            if (this.lineNumber != null) {
                size += 8 + this.lineNumber.length;
            }
            if (this.stackMap != null) {
                size += 8 + this.stackMap.length;
            }
            if (this.cattrs != null) {
                size += this.cattrs.getSize(this.cw, this.code.data, this.code.length, this.maxStack,
                        this.maxLocals);
            }
            out.putShort(this.cw.newUTF8("Code")).putInt(size);
            out.putShort(this.maxStack).putShort(this.maxLocals);
            out.putInt(this.code.length).putByteArray(this.code.data, 0, this.code.length);
            out.putShort(this.handlerCount);
            if (this.handlerCount > 0) {
                Handler h = this.firstHandler;
                while (h != null) {
                    out.putShort(h.start.position).putShort(h.end.position).putShort(h.handler.position)
                            .putShort(h.type);
                    h = h.next;
                }
            }
            attributeCount = 0;
            if (this.localVar != null) {
                ++attributeCount;
            }
            if (this.localVarType != null) {
                ++attributeCount;
            }
            if (this.lineNumber != null) {
                ++attributeCount;
            }
            if (this.stackMap != null) {
                ++attributeCount;
            }
            if (this.cattrs != null) {
                attributeCount += this.cattrs.getCount();
            }
            out.putShort(attributeCount);
            if (this.localVar != null) {
                out.putShort(this.cw.newUTF8("LocalVariableTable"));
                out.putInt(this.localVar.length + 2).putShort(this.localVarCount);
                out.putByteArray(this.localVar.data, 0, this.localVar.length);
            }
            if (this.localVarType != null) {
                out.putShort(this.cw.newUTF8("LocalVariableTypeTable"));
                out.putInt(this.localVarType.length + 2).putShort(this.localVarTypeCount);
                out.putByteArray(this.localVarType.data, 0, this.localVarType.length);
            }
            if (this.lineNumber != null) {
                out.putShort(this.cw.newUTF8("LineNumberTable"));
                out.putInt(this.lineNumber.length + 2).putShort(this.lineNumberCount);
                out.putByteArray(this.lineNumber.data, 0, this.lineNumber.length);
            }
            if (this.stackMap != null) {
                boolean zip = (this.cw.version & 0xFFFF) >= Opcodes.V1_6;
                out.putShort(this.cw.newUTF8(zip ? "StackMapTable" : "StackMap"));
                out.putInt(this.stackMap.length + 2).putShort(this.frameCount);
                out.putByteArray(this.stackMap.data, 0, this.stackMap.length);
            }
            if (this.cattrs != null) {
                this.cattrs.put(this.cw, this.code.data, this.code.length, this.maxLocals, this.maxStack,
                        out);
            }
        }
        if (this.exceptionCount > 0) {
            out.putShort(this.cw.newUTF8("Exceptions")).putInt(2 * this.exceptionCount + 2);
            out.putShort(this.exceptionCount);
            for (int i = 0; i < this.exceptionCount; ++i) {
                out.putShort(this.exceptions[i]);
            }
        }
        if ((this.access & Opcodes.ACC_SYNTHETIC) != 0) {
            if ((this.cw.version & 0xFFFF) < Opcodes.V1_5
                    || (this.access & ClassWriter.ACC_SYNTHETIC_ATTRIBUTE) != 0) {
                out.putShort(this.cw.newUTF8("Synthetic")).putInt(0);
            }
        }
        if ((this.access & Opcodes.ACC_DEPRECATED) != 0) {
            out.putShort(this.cw.newUTF8("Deprecated")).putInt(0);
        }
        if (ClassReader.SIGNATURES && this.signature != null) {
            out.putShort(this.cw.newUTF8("Signature")).putInt(2)
                    .putShort(this.cw.newUTF8(this.signature));
        }
        if (ClassReader.ANNOTATIONS && this.annd != null) {
            out.putShort(this.cw.newUTF8("AnnotationDefault"));
            out.putInt(this.annd.length);
            out.putByteArray(this.annd.data, 0, this.annd.length);
        }
        if (ClassReader.ANNOTATIONS && this.anns != null) {
            out.putShort(this.cw.newUTF8("RuntimeVisibleAnnotations"));
            this.anns.put(out);
        }
        if (ClassReader.ANNOTATIONS && this.ianns != null) {
            out.putShort(this.cw.newUTF8("RuntimeInvisibleAnnotations"));
            this.ianns.put(out);
        }
        if (ClassReader.ANNOTATIONS && this.panns != null) {
            out.putShort(this.cw.newUTF8("RuntimeVisibleParameterAnnotations"));
            AnnotationWriter.put(this.panns, this.synthetics, out);
        }
        if (ClassReader.ANNOTATIONS && this.ipanns != null) {
            out.putShort(this.cw.newUTF8("RuntimeInvisibleParameterAnnotations"));
            AnnotationWriter.put(this.ipanns, this.synthetics, out);
        }
        if (this.attrs != null) {
            this.attrs.put(this.cw, null, 0, -1, -1, out);
        }
    }

    // ------------------------------------------------------------------------
    // Utility methods: instruction resizing (used to handle GOTO_W and JSR_W)
    // ------------------------------------------------------------------------

    /**
     * Resizes and replaces the temporary instructions inserted by {@link Label#resolve} for wide
     * forward jumps, while keeping jump offsets and instruction addresses consistent. This may
     * require to resize other existing instructions, or even to introduce new instructions: for
     * example, increasing the size of an instruction by 2 at the middle of a method can increases the
     * offset of an IFEQ instruction from 32766 to 32768, in which case IFEQ 32766 must be replaced
     * with IFNEQ 8 GOTO_W 32765. This, in turn, may require to increase the size of another jump
     * instruction, and so on... All these operations are handled automatically by this method.
     * <p>
     * <i>This method must be called after all the method that is being built has been visited</i>. In
     * particular, the {@link Label Label} objects used to construct the method are no longer valid
     * after this method has been called.
     */
    private void resizeInstructions() {
        byte[] b = this.code.data; // bytecode of the method
        int u, v, label; // indexes in b
        int i, j; // loop indexes
        /*
         * 1st step: As explained above, resizing an instruction may require to resize another one,
         * which may require to resize yet another one, and so on. The first step of the algorithm
         * consists in finding all the instructions that need to be resized, without modifying the codegen.
         * This is done by the following "fix point" algorithm:
         *
         * Parse the codegen to find the jump instructions whose offset will need more than 2 bytes to be
         * stored (the future offset is computed from the current offset and from the number of bytes
         * that will be inserted or removed between the source and target instructions). For each such
         * instruction, adds an entry in (a copy of) the indexes and sizes arrays (if this has not
         * already been done in a previous iteration!).
         *
         * If at least one entry has been added during the previous step, go back to the beginning,
         * otherwise stop.
         *
         * In fact the real algorithm is complicated by the fact that the size of TABLESWITCH and
         * LOOKUPSWITCH instructions depends on their position in the bytecode (because of padding). In
         * order to ensure the convergence of the algorithm, the number of bytes to be added or removed
         * from these instructions is over estimated during the previous loop, and computed exactly only
         * after the loop is finished (this requires another pass to parse the bytecode of the method).
         */
        int[] allIndexes = new int[0]; // copy of indexes
        int[] allSizes = new int[0]; // copy of sizes
        boolean[] resize; // instructions to be resized
        int newOffset; // future offset of a jump instruction

        resize = new boolean[this.code.length];

        // 3 = loop again, 2 = loop ended, 1 = last pass, 0 = done
        int state = 3;
        do {
            if (state == 3) {
                state = 2;
            }
            u = 0;
            while (u < b.length) {
                int opcode = b[u] & 0xFF; // opcode of current instruction
                int insert = 0; // bytes to be added after this instruction

                switch (ClassWriter.TYPE[opcode]) {
                    case ClassWriter.NOARG_INSN:
                    case ClassWriter.IMPLVAR_INSN:
                        u += 1;
                        break;
                    case ClassWriter.LABEL_INSN:
                        if (opcode > 201) {
                            // converts temporary opcodes 202 to 217, 218 and
                            // 219 to IFEQ ... JSR (inclusive), IFNULL and
                            // IFNONNULL
                            opcode = opcode < 218 ? opcode - 49 : opcode - 20;
                            label = u + readUnsignedShort(b, u + 1);
                        } else {
                            label = u + readShort(b, u + 1);
                        }
                        newOffset = getNewOffset(allIndexes, allSizes, u, label);
                        if (newOffset < Short.MIN_VALUE || newOffset > Short.MAX_VALUE) {
                            if (!resize[u]) {
                                if (opcode == Opcodes.GOTO || opcode == Opcodes.JSR) {
                                    // two additional bytes will be required to
                                    // replace this GOTO or JSR instruction with
                                    // a GOTO_W or a JSR_W
                                    insert = 2;
                                } else {
                                    // five additional bytes will be required to
                                    // replace this IFxxx <l> instruction with
                                    // IFNOTxxx <l'> GOTO_W <l>, where IFNOTxxx
                                    // is the "opposite" opcode of IFxxx (i.e.,
                                    // IFNE for IFEQ) and where <l'> designates
                                    // the instruction just after the GOTO_W.
                                    insert = 5;
                                }
                                resize[u] = true;
                            }
                        }
                        u += 3;
                        break;
                    case ClassWriter.LABELW_INSN:
                        u += 5;
                        break;
                    case ClassWriter.TABL_INSN:
                        if (state == 1) {
                            // true number of bytes to be added (or removed)
                            // from this instruction = (future number of padding
                            // bytes - current number of padding byte) -
                            // previously over estimated variation =
                            // = ((3 - newOffset%4) - (3 - u%4)) - u%4
                            // = (-newOffset%4 + u%4) - u%4
                            // = -(newOffset & 3)
                            newOffset = getNewOffset(allIndexes, allSizes, 0, u);
                            insert = -(newOffset & 3);
                        } else if (!resize[u]) {
                            // over estimation of the number of bytes to be
                            // added to this instruction = 3 - current number
                            // of padding bytes = 3 - (3 - u%4) = u%4 = u & 3
                            insert = u & 3;
                            resize[u] = true;
                        }
                        // skips instruction
                        u = u + 4 - (u & 3);
                        u += 4 * (readInt(b, u + 8) - readInt(b, u + 4) + 1) + 12;
                        break;
                    case ClassWriter.LOOK_INSN:
                        if (state == 1) {
                            // like TABL_INSN
                            newOffset = getNewOffset(allIndexes, allSizes, 0, u);
                            insert = -(newOffset & 3);
                        } else if (!resize[u]) {
                            // like TABL_INSN
                            insert = u & 3;
                            resize[u] = true;
                        }
                        // skips instruction
                        u = u + 4 - (u & 3);
                        u += 8 * readInt(b, u + 4) + 8;
                        break;
                    case ClassWriter.WIDE_INSN:
                        opcode = b[u + 1] & 0xFF;
                        if (opcode == Opcodes.IINC) {
                            u += 6;
                        } else {
                            u += 4;
                        }
                        break;
                    case ClassWriter.VAR_INSN:
                    case ClassWriter.SBYTE_INSN:
                    case ClassWriter.LDC_INSN:
                        u += 2;
                        break;
                    case ClassWriter.SHORT_INSN:
                    case ClassWriter.LDCW_INSN:
                    case ClassWriter.FIELDORMETH_INSN:
                    case ClassWriter.TYPE_INSN:
                    case ClassWriter.IINC_INSN:
                        u += 3;
                        break;
                    case ClassWriter.ITFMETH_INSN:
                    case ClassWriter.INDYMETH_INSN:
                        u += 5;
                        break;
                    // case ClassWriter.MANA_INSN:
                    default:
                        u += 4;
                        break;
                }
                if (insert != 0) {
                    // adds a new (u, insert) entry in the allIndexes and
                    // allSizes arrays
                    int[] newIndexes = new int[allIndexes.length + 1];
                    int[] newSizes = new int[allSizes.length + 1];
                    System.arraycopy(allIndexes, 0, newIndexes, 0, allIndexes.length);
                    System.arraycopy(allSizes, 0, newSizes, 0, allSizes.length);
                    newIndexes[allIndexes.length] = u;
                    newSizes[allSizes.length] = insert;
                    allIndexes = newIndexes;
                    allSizes = newSizes;
                    if (insert > 0) {
                        state = 3;
                    }
                }
            }
            if (state < 3) {
                --state;
            }
        } while (state != 0);

        // 2nd step:
        // copies the bytecode of the method into a new bytevector, updates the
        // offsets, and inserts (or removes) bytes as requested.

        ByteVector newCode = new ByteVector(this.code.length);

        u = 0;
        while (u < this.code.length) {
            int opcode = b[u] & 0xFF;
            switch (ClassWriter.TYPE[opcode]) {
                case ClassWriter.NOARG_INSN:
                case ClassWriter.IMPLVAR_INSN:
                    newCode.putByte(opcode);
                    u += 1;
                    break;
                case ClassWriter.LABEL_INSN:
                    if (opcode > 201) {
                        // changes temporary opcodes 202 to 217 (inclusive), 218
                        // and 219 to IFEQ ... JSR (inclusive), IFNULL and
                        // IFNONNULL
                        opcode = opcode < 218 ? opcode - 49 : opcode - 20;
                        label = u + readUnsignedShort(b, u + 1);
                    } else {
                        label = u + readShort(b, u + 1);
                    }
                    newOffset = getNewOffset(allIndexes, allSizes, u, label);
                    if (resize[u]) {
                        // replaces GOTO with GOTO_W, JSR with JSR_W and IFxxx
                        // <l> with IFNOTxxx <l'> GOTO_W <l>, where IFNOTxxx is
                        // the "opposite" opcode of IFxxx (i.e., IFNE for IFEQ)
                        // and where <l'> designates the instruction just after
                        // the GOTO_W.
                        if (opcode == Opcodes.GOTO) {
                            newCode.putByte(200); // GOTO_W
                        } else if (opcode == Opcodes.JSR) {
                            newCode.putByte(201); // JSR_W
                        } else {
                            newCode.putByte(opcode <= 166 ? ((opcode + 1) ^ 1) - 1 : opcode ^ 1);
                            newCode.putShort(8); // jump offset
                            newCode.putByte(200); // GOTO_W
                            // newOffset now computed from start of GOTO_W
                            newOffset -= 3;
                        }
                        newCode.putInt(newOffset);
                    } else {
                        newCode.putByte(opcode);
                        newCode.putShort(newOffset);
                    }
                    u += 3;
                    break;
                case ClassWriter.LABELW_INSN:
                    label = u + readInt(b, u + 1);
                    newOffset = getNewOffset(allIndexes, allSizes, u, label);
                    newCode.putByte(opcode);
                    newCode.putInt(newOffset);
                    u += 5;
                    break;
                case ClassWriter.TABL_INSN:
                    // skips 0 to 3 padding bytes
                    v = u;
                    u = u + 4 - (v & 3);
                    // reads and copies instruction
                    newCode.putByte(Opcodes.TABLESWITCH);
                    newCode.putByteArray(null, 0, (4 - newCode.length % 4) % 4);
                    label = v + readInt(b, u);
                    u += 4;
                    newOffset = getNewOffset(allIndexes, allSizes, v, label);
                    newCode.putInt(newOffset);
                    j = readInt(b, u);
                    u += 4;
                    newCode.putInt(j);
                    j = readInt(b, u) - j + 1;
                    u += 4;
                    newCode.putInt(readInt(b, u - 4));
                    for (; j > 0; --j) {
                        label = v + readInt(b, u);
                        u += 4;
                        newOffset = getNewOffset(allIndexes, allSizes, v, label);
                        newCode.putInt(newOffset);
                    }
                    break;
                case ClassWriter.LOOK_INSN:
                    // skips 0 to 3 padding bytes
                    v = u;
                    u = u + 4 - (v & 3);
                    // reads and copies instruction
                    newCode.putByte(Opcodes.LOOKUPSWITCH);
                    newCode.putByteArray(null, 0, (4 - newCode.length % 4) % 4);
                    label = v + readInt(b, u);
                    u += 4;
                    newOffset = getNewOffset(allIndexes, allSizes, v, label);
                    newCode.putInt(newOffset);
                    j = readInt(b, u);
                    u += 4;
                    newCode.putInt(j);
                    for (; j > 0; --j) {
                        newCode.putInt(readInt(b, u));
                        u += 4;
                        label = v + readInt(b, u);
                        u += 4;
                        newOffset = getNewOffset(allIndexes, allSizes, v, label);
                        newCode.putInt(newOffset);
                    }
                    break;
                case ClassWriter.WIDE_INSN:
                    opcode = b[u + 1] & 0xFF;
                    if (opcode == Opcodes.IINC) {
                        newCode.putByteArray(b, u, 6);
                        u += 6;
                    } else {
                        newCode.putByteArray(b, u, 4);
                        u += 4;
                    }
                    break;
                case ClassWriter.VAR_INSN:
                case ClassWriter.SBYTE_INSN:
                case ClassWriter.LDC_INSN:
                    newCode.putByteArray(b, u, 2);
                    u += 2;
                    break;
                case ClassWriter.SHORT_INSN:
                case ClassWriter.LDCW_INSN:
                case ClassWriter.FIELDORMETH_INSN:
                case ClassWriter.TYPE_INSN:
                case ClassWriter.IINC_INSN:
                    newCode.putByteArray(b, u, 3);
                    u += 3;
                    break;
                case ClassWriter.ITFMETH_INSN:
                case ClassWriter.INDYMETH_INSN:
                    newCode.putByteArray(b, u, 5);
                    u += 5;
                    break;
                // case MANA_INSN:
                default:
                    newCode.putByteArray(b, u, 4);
                    u += 4;
                    break;
            }
        }

        // recomputes the stack map frames
        if (this.frameCount > 0) {
            if (this.compute == FRAMES) {
                this.frameCount = 0;
                this.stackMap = null;
                this.previousFrame = null;
                this.frame = null;
                Frame f = new Frame();
                f.owner = this.labels;
                Type[] args = Type.getArgumentTypes(this.descriptor);
                f.initInputFrame(this.cw, this.access, args, this.maxLocals);
                visitFrame(f);
                Label l = this.labels;
                while (l != null) {
                    /*
                     * here we need the original label position. getNewOffset must therefore never have been
                     * called for this label.
                     */
                    u = l.position - 3;
                    if ((l.status & Label.STORE) != 0 || (u >= 0 && resize[u])) {
                        getNewOffset(allIndexes, allSizes, l);
                        // TODO update offsets in UNINITIALIZED values
                        visitFrame(l.frame);
                    }
                    l = l.successor;
                }
            } else {
                /*
                 * Resizing an existing stack map frame table is really hard. Not only the table must be
                 * parsed to update the offets, but new frames may be needed for jump instructions that were
                 * inserted by this method. And updating the offsets or inserting frames can change the
                 * format of the following frames, in case of packed frames. In practice the whole table
                 * must be recomputed. For this the frames are marked as potentially invalid. This will
                 * cause the whole class to be reread and rewritten with the COMPUTE_FRAMES option (see the
                 * ClassWriter.toByteArray method). This is not very efficient but is much easier and
                 * requires much less codegen than any other method I can think of.
                 */
                this.cw.invalidFrames = true;
            }
        }
        // updates the exception handler block labels
        Handler h = this.firstHandler;
        while (h != null) {
            getNewOffset(allIndexes, allSizes, h.start);
            getNewOffset(allIndexes, allSizes, h.end);
            getNewOffset(allIndexes, allSizes, h.handler);
            h = h.next;
        }
        // updates the instructions addresses in the
        // local var and line number tables
        for (i = 0; i < 2; ++i) {
            ByteVector bv = i == 0 ? this.localVar : this.localVarType;
            if (bv != null) {
                b = bv.data;
                u = 0;
                while (u < bv.length) {
                    label = readUnsignedShort(b, u);
                    newOffset = getNewOffset(allIndexes, allSizes, 0, label);
                    writeShort(b, u, newOffset);
                    label += readUnsignedShort(b, u + 2);
                    newOffset = getNewOffset(allIndexes, allSizes, 0, label) - newOffset;
                    writeShort(b, u + 2, newOffset);
                    u += 10;
                }
            }
        }
        if (this.lineNumber != null) {
            b = this.lineNumber.data;
            u = 0;
            while (u < this.lineNumber.length) {
                writeShort(b, u, getNewOffset(allIndexes, allSizes, 0, readUnsignedShort(b, u)));
                u += 4;
            }
        }
        // updates the labels of the other attributes
        Attribute attr = this.cattrs;
        while (attr != null) {
            Label[] labels = attr.getLabels();
            if (labels != null) {
                for (i = labels.length - 1; i >= 0; --i) {
                    getNewOffset(allIndexes, allSizes, labels[i]);
                }
            }
            attr = attr.next;
        }

        // replaces old bytecodes with new ones
        this.code = newCode;
    }

    /**
     * Reads an unsigned short value in the given byte array.
     *
     * @param b     a byte array.
     * @param index the start index of the value to be read.
     * @return the read value.
     */
    static int readUnsignedShort(final byte[] b, final int index) {
        return ((b[index] & 0xFF) << 8) | (b[index + 1] & 0xFF);
    }

    /**
     * Reads a signed short value in the given byte array.
     *
     * @param b     a byte array.
     * @param index the start index of the value to be read.
     * @return the read value.
     */
    static short readShort(final byte[] b, final int index) {
        return (short) (((b[index] & 0xFF) << 8) | (b[index + 1] & 0xFF));
    }

    /**
     * Reads a signed int value in the given byte array.
     *
     * @param b     a byte array.
     * @param index the start index of the value to be read.
     * @return the read value.
     */
    static int readInt(final byte[] b, final int index) {
        return ((b[index] & 0xFF) << 24) | ((b[index + 1] & 0xFF) << 16) | ((b[index + 2] & 0xFF) << 8)
                | (b[index + 3] & 0xFF);
    }

    /**
     * Writes a short value in the given byte array.
     *
     * @param b     a byte array.
     * @param index where the first byte of the short value must be written.
     * @param s     the value to be written in the given byte array.
     */
    static void writeShort(final byte[] b, final int index, final int s) {
        b[index] = (byte) (s >>> 8);
        b[index + 1] = (byte) s;
    }

    /**
     * Computes the future value of a bytecode offset.
     * <p>
     * Note: it is possible to have several entries for the same instruction in the <tt>indexes</tt>
     * and <tt>sizes</tt>: two entries (index=a,size=b) and (index=a,size=b') are equivalent to a
     * single entry (index=a,size=b+b').
     *
     * @param indexes current positions of the instructions to be resized. Each instruction must be
     *                designated by the index of its <i>last</i> byte, plus one (or, in other words, by the
     *                index of the <i>first</i> byte of the <i>next</i> instruction).
     * @param sizes   the number of bytes to be <i>added</i> to the above instructions. More precisely,
     *                for each i < <tt>len</tt>, <tt>sizes</tt>[i] bytes will be added at the end of the
     *                instruction designated by <tt>indexes</tt>[i] or, if <tt>sizes</tt>[i] is negative, the
     *                <i>last</i> | <tt>sizes[i]</tt>| bytes of the instruction will be removed (the
     *                instruction size <i>must not</i> become negative or null).
     * @param begin   index of the first byte of the source instruction.
     * @param end     index of the first byte of the target instruction.
     * @return the future value of the given bytecode offset.
     */
    static int getNewOffset(final int[] indexes, final int[] sizes, final int begin, final int end) {
        int offset = end - begin;
        for (int i = 0; i < indexes.length; ++i) {
            if (begin < indexes[i] && indexes[i] <= end) {
                // forward jump
                offset += sizes[i];
            } else if (end < indexes[i] && indexes[i] <= begin) {
                // backward jump
                offset -= sizes[i];
            }
        }
        return offset;
    }

    /**
     * Updates the offset of the given label.
     *
     * @param indexes current positions of the instructions to be resized. Each instruction must be
     *                designated by the index of its <i>last</i> byte, plus one (or, in other words, by the
     *                index of the <i>first</i> byte of the <i>next</i> instruction).
     * @param sizes   the number of bytes to be <i>added</i> to the above instructions. More precisely,
     *                for each i < <tt>len</tt>, <tt>sizes</tt>[i] bytes will be added at the end of the
     *                instruction designated by <tt>indexes</tt>[i] or, if <tt>sizes</tt>[i] is negative, the
     *                <i>last</i> | <tt>sizes[i]</tt>| bytes of the instruction will be removed (the
     *                instruction size <i>must not</i> become negative or null).
     * @param label   the label whose offset must be updated.
     */
    static void getNewOffset(final int[] indexes, final int[] sizes, final Label label) {
        if ((label.status & Label.RESIZED) == 0) {
            label.position = getNewOffset(indexes, sizes, 0, label.position);
            label.status |= Label.RESIZED;
        }
    }
}
