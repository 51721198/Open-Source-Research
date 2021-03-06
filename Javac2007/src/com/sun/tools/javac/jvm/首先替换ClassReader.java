///*
// * @(#)ClassReader.java	1.138 07/03/21
// * 
// * Copyright (c) 2007 Sun Microsystems, Inc.  All Rights Reserved.
// * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
// *  
// * This code is free software; you can redistribute it and/or modify it
// * under the terms of the GNU General Public License version 2 only, as
// * published by the Free Software Foundation.  Sun designates this
// * particular file as subject to the "Classpath" exception as provided
// * by Sun in the LICENSE file that accompanied this code.
// *  
// * This code is distributed in the hope that it will be useful, but WITHOUT
// * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// * version 2 for more details (a copy is included in the LICENSE file that
// * accompanied this code).
// *  
// * You should have received a copy of the GNU General Public License version
// * 2 along with this work; if not, write to the Free Software Foundation,
// * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
// *  
// * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
// * CA 95054 USA or visit www.sun.com if you need additional information or
// * have any questions.
// */
//
//package com.sun.tools.javac.jvm;
//
//import java.io.*;
//import java.net.URI;
//import java.nio.CharBuffer;
//import java.util.EnumSet;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Set;
//import javax.lang.model.SourceVersion;
//import javax.tools.JavaFileObject;
//import javax.tools.JavaFileManager;
//import javax.tools.StandardJavaFileManager;
//
//import com.sun.tools.javac.comp.Annotate;
//import com.sun.tools.javac.code.*;
//import com.sun.tools.javac.code.Type.*;
//import com.sun.tools.javac.code.Symbol.*;
//import com.sun.tools.javac.code.Symtab;
//import com.sun.tools.javac.util.*;
//import com.sun.tools.javac.util.List;
//
//import static com.sun.tools.javac.code.Flags.*;
//import static com.sun.tools.javac.code.Kinds.*;
//import static com.sun.tools.javac.code.TypeTags.*;
//import com.sun.tools.javac.jvm.ClassFile.NameAndType;
//import javax.tools.JavaFileManager.Location;
//import static javax.tools.StandardLocation.*;
//
///** This class provides operations to read a classfile into an internal
// *  representation. The internal representation is anchored in a
// *  ClassSymbol which contains in its scope symbol representations
// *  for all other definitions in the classfile. Top-level Classes themselves
// *  appear as members of the scopes of PackageSymbols.
// *
// *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
// *  you write code that depends on this, you do so at your own risk.
// *  This code and its internal interfaces are subject to change or
// *  deletion without notice.</b>
// */
//@Version("@(#)ClassReader.java	1.138 07/03/21")
//public class ClassReader extends ClassFile implements Completer {
//    private static my.Debug DEBUG=new my.Debug(my.Debug.ClassReader);//我加上的
//	
//    /** The context key for the class reader. */
//    protected static final Context.Key<ClassReader> classReaderKey =
//        new Context.Key<ClassReader>();
//
//    Annotate annotate;
//
//    /** Switch: verbose output.
//     */
//    boolean verbose;
//
//    /** Switch: check class file for correct minor version, unrecognized
//     *  attributes.
//     */
//    boolean checkClassFile;
//
//    /** Switch: read constant pool and code sections. This switch is initially
//     *  set to false but can be turned on from outside.
//     */
//    public boolean readAllOfClassFile = false;
//
//    /** Switch: read GJ signature information.
//     */
//    boolean allowGenerics;
//
//    /** Switch: read varargs attribute.
//     */
//    boolean allowVarargs;
//
//    /** Switch: allow annotations.
//     */
//    boolean allowAnnotations;
//
//    /** Switch: preserve parameter names from the variable table.
//     */
//    public boolean saveParameterNames;
//
//    /**
//     * Switch: cache completion failures unless -XDdev is used
//     */
//    private boolean cacheCompletionFailure;
//    
//    /**
//     * Switch: prefer source files instead of newer when both source 
//     * and class are available
//     **/
//    public boolean preferSource;
//
//    /** The log to use for verbose output
//     */
//    final Log log;
//
//    /** The symbol table. */
//    Symtab syms;
//
//    Types types;
//
//    /** The name table. */
//    final Name.Table names;
//
//    /** Force a completion failure on this name
//     */
//    final Name completionFailureName;
//
//    /** Access to files
//     */
//    private final JavaFileManager fileManager;
//
//    /** Can be reassigned from outside:
//     *  the completer to be used for ".java" files. If this remains unassigned
//     *  ".java" files will not be loaded.
//     */
//    public SourceCompleter sourceCompleter = null;//在JavaCompiler(final Context context)赋值
//
//    /** A hashtable containing the encountered top-level and member classes,
//     *  indexed by flat names. The table does not contain local classes.
//     */
//    private Map<Name,ClassSymbol> classes;
//
//    /** A hashtable containing the encountered packages.
//     */
//    private Map<Name, PackageSymbol> packages;
//
//    /** The current scope where type variables are entered.
//     */
//    protected Scope typevars;
//
//    /** The path name of the class file currently being read.
//     */
//    protected JavaFileObject currentClassFile = null;
//
//    /** The class or method currently being read.
//     */
//    protected Symbol currentOwner = null;
//
//    /** The buffer containing the currently read class file.
//     */
//    byte[] buf = new byte[0x0fff0];
//
//    /** The current input pointer.
//     */
//    int bp;
//
//    /** The objects of the constant pool.
//     */
//    Object[] poolObj;
//
//    /** For every constant pool entry, an index into buf where the
//     *  defining section of the entry is found.
//     */
//    int[] poolIdx;
//
//    /** Get the ClassReader instance for this invocation. */
//    public static ClassReader instance(Context context) {
//        ClassReader instance = context.get(classReaderKey);
//        if (instance == null)
//            instance = new ClassReader(context, true);
//        return instance;
//    }
//
//    /** Initialize classes and packages, treating this as the definitive classreader. */
//    public void init(Symtab syms) {
//    	DEBUG.P(this,"init(1)");
//        init(syms, true);
//        DEBUG.P(1,this,"init(1)");
//    }
//
//    /** Initialize classes and packages, optionally treating this as
//     *  the definitive classreader.
//     */
//    private void init(Symtab syms, boolean definitive) {
//        if (classes != null) return;
//
//        if (definitive) {
//            assert packages == null || packages == syms.packages;
//            packages = syms.packages;
//            assert classes == null || classes == syms.classes;
//            classes = syms.classes;
//        } else {
//            packages = new HashMap<Name, PackageSymbol>();
//            classes = new HashMap<Name, ClassSymbol>();
//        }
//
//        packages.put(names.empty, syms.rootPackage);
//        syms.rootPackage.completer = this;
//        syms.unnamedPackage.completer = this;
//        
//        DEBUG.P("将<names.empty, syms.rootPackage>加进Map<Name, PackageSymbol> packages");
//        DEBUG.P("将rootPackage unnamedPackage的Symbol.Completer置为ClassReader");
//    }
//
//    /** Construct a new class reader, optionally treated as the
//     *  definitive classreader for this invocation.
//     */
//    protected ClassReader(Context context, boolean definitive) {
//        //当definitive=false的时候得到一个新的ClassReader的实例，
//        //这个实例变量的classes与packages字段就跟Symtab类中的不一样
//        //Symtab类中的classes与packages字段含有系统默认的类和包
//        //如果definitive=true的时候就是指向Symtab类中的classes与packages字段
//    	DEBUG.P(this,"ClassReader(2)");
//        if (definitive) context.put(classReaderKey, this);
//
//        names = Name.Table.instance(context);
//        syms = Symtab.instance(context);
//        types = Types.instance(context);
//        fileManager = context.get(JavaFileManager.class);
//        if (fileManager == null)
//            throw new AssertionError("FileManager initialization error");
//
//        init(syms, definitive);
//        log = Log.instance(context);
//
//        Options options = Options.instance(context);
//        annotate = Annotate.instance(context);
//        verbose        = options.get("-verbose")        != null;
//        checkClassFile = options.get("-checkclassfile") != null;
//        Source source = Source.instance(context);
//        allowGenerics    = source.allowGenerics();
//        allowVarargs     = source.allowVarargs();
//        allowAnnotations = source.allowAnnotations();
//        
//        //在初始化完ClassReader后，在JavaCompiler的initProcessAnnotations(1)方法中也会
//        //根据情况设置
//        saveParameterNames = options.get("save-parameter-names") != null;
//        
//        cacheCompletionFailure = options.get("dev") == null;
//        preferSource = "source".equals(options.get("-Xprefer"));
//
//        completionFailureName =
//            (options.get("failcomplete") != null)
//            ? names.fromString(options.get("failcomplete"))
//            : null;
//
//        typevars = new Scope(syms.noSymbol);
//        
//        DEBUG.P(1,this,"ClassReader(2)");
//    }
//
//    /** Add member to class unless it is synthetic.
//     */
//    private void enterMember(ClassSymbol c, Symbol sym) {
//    	//只有flags_field单单含有SYNTHETIC时才为false，
//    	//其他情况(包括同时含有SYNTHETIC与BRIDGE)都为true
//        if ((sym.flags_field & (SYNTHETIC|BRIDGE)) != SYNTHETIC)
//            c.members_field.enter(sym);
//    }
//
///************************************************************************
// * Error Diagnoses
// ***********************************************************************/
//
//    public static class BadClassFile extends CompletionFailure {
//        private static final long serialVersionUID = 0;
//
//        /**
//         * @param msg A localized message.
//         */
//        public BadClassFile(ClassSymbol c, Object cname, Object msg) {
//            super(c, Log.getLocalizedString("bad.class.file.header",
//                                            cname, msg));
//        }
//    }
//
//    public BadClassFile badClassFile(String key, Object... args) {
//        try {//我加上的
//        DEBUG.P(this,"badClassFile(2)");
//        DEBUG.P("key="+key);
//        DEBUG.P("currentOwner.enclClass()="+currentOwner.enclClass());
//        DEBUG.P("currentClassFile="+currentClassFile);
//        
//        return new BadClassFile (
//            currentOwner.enclClass(),
//            currentClassFile,
//            Log.getLocalizedString(key, args));
//        
//        } finally {
//        DEBUG.P(0,this,"badClassFile(2)");    
//        }
//    }
//
///************************************************************************
// * Buffer Access
// ***********************************************************************/
//
//    /** Read a character.
//     */
//    char nextChar() {
//        return (char)(((buf[bp++] & 0xFF) << 8) + (buf[bp++] & 0xFF));
//        
//        //下面这种方式跟上面有什么区别？？？？？？？？
//		//与0xFF相与不会取负值
//        //return (char)(((buf[bp++]) << 8) + (buf[bp++]));
//    }
//
//    /** Read an integer.
//     */
//    int nextInt() {
//        return
//            ((buf[bp++] & 0xFF) << 24) +
//            ((buf[bp++] & 0xFF) << 16) +
//            ((buf[bp++] & 0xFF) << 8) +
//            (buf[bp++] & 0xFF);
//    }
//
//    /** Extract a character at position bp from buf.
//     */
//    char getChar(int bp) {
//        return
//            (char)(((buf[bp] & 0xFF) << 8) + (buf[bp+1] & 0xFF));
//    }
//
//    /** Extract an integer at position bp from buf.
//     */
//    int getInt(int bp) {
//        return
//            ((buf[bp] & 0xFF) << 24) +
//            ((buf[bp+1] & 0xFF) << 16) +
//            ((buf[bp+2] & 0xFF) << 8) +
//            (buf[bp+3] & 0xFF);
//    }
//
//
//    /** Extract a long integer at position bp from buf.
//     */
//    long getLong(int bp) {
//        DataInputStream bufin =
//            new DataInputStream(new ByteArrayInputStream(buf, bp, 8));
//        try {
//            return bufin.readLong();
//        } catch (IOException e) {
//            throw new AssertionError(e);
//        }
//    }
//
//    /** Extract a float at position bp from buf.
//     */
//    float getFloat(int bp) {
//        DataInputStream bufin =
//            new DataInputStream(new ByteArrayInputStream(buf, bp, 4));
//        try {
//            return bufin.readFloat();
//        } catch (IOException e) {
//            throw new AssertionError(e);
//        }
//    }
//
//    /** Extract a double at position bp from buf.
//     */
//    double getDouble(int bp) {
//        DataInputStream bufin =
//            new DataInputStream(new ByteArrayInputStream(buf, bp, 8));
//        try {
//            return bufin.readDouble();
//        } catch (IOException e) {
//            throw new AssertionError(e);
//        }
//    }
//
///************************************************************************
// * Constant Pool Access
// ***********************************************************************/
//
//    /** Index all constant pool entries, writing their start addresses into
//     *  poolIdx.
//     */
//    void indexPool() {
//    	try {//我加上的
//		DEBUG.P(this,"indexPool()");
//		
//        poolIdx = new int[nextChar()];
//        poolObj = new Object[poolIdx.length];
//        
//        DEBUG.P("poolIdx.length="+poolIdx.length);
//        
//        int i = 1;//常量池索引0保留不用
//        while (i < poolIdx.length) {
//            poolIdx[i++] = bp;
//            byte tag = buf[bp++];
//            //DEBUG.P("i="+(i-1)+" tag="+tag+" bp="+(bp-1));
//            switch (tag) {
//            case CONSTANT_Utf8: case CONSTANT_Unicode: {
//                int len = nextChar();
//                bp = bp + len;
//                break;
//            }
//            case CONSTANT_Class:
//            case CONSTANT_String:
//                bp = bp + 2;
//                break;
//            case CONSTANT_Fieldref:
//            case CONSTANT_Methodref:
//            case CONSTANT_InterfaceMethodref:
//            case CONSTANT_NameandType:
//            case CONSTANT_Integer:
//            case CONSTANT_Float:
//                bp = bp + 4;
//                break;
//            case CONSTANT_Long:
//            case CONSTANT_Double:
//                bp = bp + 8;
//                i++;
//                break;
//            default:
//                throw badClassFile("bad.const.pool.tag.at",
//                                   Byte.toString(tag),
//                                   Integer.toString(bp -1));
//            }
//        }
//        
//        StringBuffer sb=new StringBuffer();
//        for(int n:poolIdx) sb.append(n).append(" ");
//        DEBUG.P("poolIdx="+sb.toString());
//        
//        }finally{//我加上的
//		DEBUG.P(0,this,"indexPool()");
//		}
//    }
//
//    /** Read constant pool entry at start address i, use pool as a cache.
//     */
//    //注意:参数i不是constant pool entry的start address，而是constant pool entry
//    //在constant pool的索引
//    Object readPool(int i) {
//		try {//我加上的
//		DEBUG.P(this,"readPool(1)");
//
//        Object result = poolObj[i];
//
//		DEBUG.P("i="+i);
//		DEBUG.P("result="+result);
//
//        if (result != null) return result;
//
//        int index = poolIdx[i];
//        if (index == 0) return null;
//
//        byte tag = buf[index];
//		DEBUG.P("tag="+myTAG(tag));
//        switch (tag) {
//        case CONSTANT_Utf8:
//            poolObj[i] = names.fromUtf(buf, index + 3, getChar(index + 1));
//            break;
//        case CONSTANT_Unicode:
//            throw badClassFile("unicode.str.not.supported");
//        case CONSTANT_Class:
//            poolObj[i] = readClassOrType(getChar(index + 1));
//            break;
//        case CONSTANT_String:
//            // FIXME: (footprint) do not use toString here
//            poolObj[i] = readName(getChar(index + 1)).toString();
//            break;
//        case CONSTANT_Fieldref: {
//            ClassSymbol owner = readClassSymbol(getChar(index + 1));
//            NameAndType nt = (NameAndType)readPool(getChar(index + 3));
//            poolObj[i] = new VarSymbol(0, nt.name, nt.type, owner);
//            break;
//        }
//        case CONSTANT_Methodref:
//        case CONSTANT_InterfaceMethodref: {
//            ClassSymbol owner = readClassSymbol(getChar(index + 1));
//            NameAndType nt = (NameAndType)readPool(getChar(index + 3));
//            poolObj[i] = new MethodSymbol(0, nt.name, nt.type, owner);
//            break;
//        }
//        case CONSTANT_NameandType:
//            poolObj[i] = new NameAndType(
//                readName(getChar(index + 1)),
//                readType(getChar(index + 3)));
//            break;
//        case CONSTANT_Integer:
//            poolObj[i] = getInt(index + 1);
//            break;
//        case CONSTANT_Float:
//            poolObj[i] = new Float(getFloat(index + 1));
//            break;
//        case CONSTANT_Long:
//            poolObj[i] = new Long(getLong(index + 1));
//            break;
//        case CONSTANT_Double:
//            poolObj[i] = new Double(getDouble(index + 1));
//            break;
//        default:
//            throw badClassFile("bad.const.pool.tag", Byte.toString(tag));
//        }
//		DEBUG.P("poolObj[i]="+poolObj[i]);
//        return poolObj[i];
//
//		}finally{//我加上的
//		DEBUG.P(0,this,"readPool(1)");
//		}
//    }
//
//    /** Read signature and convert to type.
//     */
//    Type readType(int i) {
//        int index = poolIdx[i];//CONSTANT_Utf8类型
//        return sigToType(buf, index + 3, getChar(index + 1));
//    }
//
//    /** If name is an array type or class signature, return the
//     *  corresponding type; otherwise return a ClassSymbol with given name.
//     */
//    Object readClassOrType(int i) {
//    	try {//我加上的
//		DEBUG.P(this,"readClassOrType(1)");
//		DEBUG.P("i="+i);
//
//        int index =  poolIdx[i];
//        int len = getChar(index + 1);
//        int start = index + 3;
//        
//        DEBUG.P("index="+index);
//        DEBUG.P("len="+len);
//        DEBUG.P("start="+start);
//        DEBUG.P("buf[start]="+(char)buf[start]);
//        DEBUG.P("buf[start + len - 1]="+(char)buf[start + len - 1]);
//        
//        assert buf[start] == '[' || buf[start + len - 1] != ';';
//        // by the above assertion, the following test can be
//        // simplified to (buf[start] == '[')
//        return (buf[start] == '[' || buf[start + len - 1] == ';')
//            ? (Object)sigToType(buf, start, len)
//            : (Object)enterClass(names.fromUtf(internalize(buf, start,
//                                                           len)));
//        }finally{//我加上的
//		DEBUG.P(0,this,"readClassOrType(1)");
//		}                                                                                                
//    }
//
//    /** Read signature and convert to type parameters.
//     */
//    List<Type> readTypeParams(int i) {
//    	try {//我加上的
//		DEBUG.P(this,"readTypeParams(1)");
//		DEBUG.P("i="+i);
//	//i是常量池索引，且对应tag是CONSTANT_Utf8类型	
//        int index = poolIdx[i];
//        
//        DEBUG.P("index="+index);
//        //getChar(index + 1)是字节长度
//        //index+3表示signature的开始位置
//        return sigToTypeParams(buf, index + 3, getChar(index + 1));
//        
//        }finally{//我加上的
//		DEBUG.P(0,this,"readTypeParams(1)");
//		}
//    }
//
//    /** Read class entry.
//     */
//    ClassSymbol readClassSymbol(int i) {
//		try {//我加上的
//		DEBUG.P(this,"readClassSymbol(1)");
//
//        return (ClassSymbol) (readPool(i));
//
//		}finally{//我加上的
//		DEBUG.P(0,this,"readClassSymbol(1)");
//		}
//    }
//
//    /** Read name.
//     */
//    Name readName(int i) {
//    	try {//我加上的
//		DEBUG.P(this,"readName(1)");
//		
//        return (Name) (readPool(i));
//        
//        }finally{//我加上的
//		DEBUG.P(0,this,"readName(1)");
//		}
//    }
//
///************************************************************************
// * Reading Types
// ***********************************************************************/
//
//    /** The unread portion of the currently read type is
//     *  signature[sigp..siglimit-1].
//     */
//    byte[] signature;
//    int sigp;
//    int siglimit;
//    boolean sigEnterPhase = false;
//
//    /** Convert signature to type, where signature is a name.
//     */
//    Type sigToType(Name sig) {
//		try {//我加上的
//		DEBUG.P(this,"sigToType(1)");
//		DEBUG.P("sig="+sig);
//
//        return sig == null
//            ? null
//            : sigToType(sig.table.names, sig.index, sig.len);
//
//		}finally{//我加上的
//		DEBUG.P(0,this,"sigToType(1)");
//		}
//    }
//
//    /** Convert signature to type, where signature is a byte array segment.
//     */
//    Type sigToType(byte[] sig, int offset, int len) {
//		try {//我加上的
//		DEBUG.P(this,"sigToType(3)");
//		DEBUG.P("offset="+offset);
//		DEBUG.P("len="+len);
//
//        signature = sig;
//        sigp = offset;
//        siglimit = offset + len;
//        return sigToType();
//
//		}finally{//我加上的
//		DEBUG.P(0,this,"sigToType(3)");
//		}
//    }
//
//    /** Convert signature to type, where signature is implicit.
//     */
//    Type sigToType() {
//		try {//我加上的
//		DEBUG.P(this,"sigToType()");
//		DEBUG.P("signature[sigp]="+(char)signature[sigp]);
//
//        switch ((char) signature[sigp]) {
//        case 'T':
//            sigp++;
//            int start = sigp;
//			DEBUG.P("signature[sigp]="+(char)signature[sigp]);
//            while (signature[sigp] != ';') sigp++;
//            sigp++;
//			DEBUG.P("sigEnterPhase="+sigEnterPhase);
//            return sigEnterPhase
//                ? Type.noType
//                : findTypeVar(names.fromUtf(signature, start, sigp - 1 - start));
//        case '+': {
//            sigp++;
//            Type t = sigToType();
//            return new WildcardType(t, BoundKind.EXTENDS,
//                                    syms.boundClass);
//        }
//        case '*':
//            sigp++;
//            return new WildcardType(syms.objectType, BoundKind.UNBOUND,
//                                    syms.boundClass);
//        case '-': {
//            sigp++;
//            Type t = sigToType();
//            return new WildcardType(t, BoundKind.SUPER,
//                                    syms.boundClass);
//        }
//        case 'B':
//            sigp++;
//            return syms.byteType;
//        case 'C':
//            sigp++;
//            return syms.charType;
//        case 'D':
//            sigp++;
//            return syms.doubleType;
//        case 'F':
//            sigp++;
//            return syms.floatType;
//        case 'I':
//            sigp++;
//            return syms.intType;
//        case 'J':
//            sigp++;
//            return syms.longType;
//        case 'L':
//            {
//                // int oldsigp = sigp;
//                Type t = classSigToType();
//				DEBUG.P("sigp="+sigp);
//				DEBUG.P("siglimit="+siglimit);
//				if(sigp < siglimit)
//					DEBUG.P("signature[sigp]="+(char)signature[sigp]);
//
//                if (sigp < siglimit && signature[sigp] == '.')
//                    throw badClassFile("deprecated inner class signature syntax " +
//                                       "(please recompile from source)");
//                /*
//                System.err.println(" decoded " +
//                                   new String(signature, oldsigp, sigp-oldsigp) +
//                                   " => " + t + " outer " + t.outer());
//                */
//                return t;
//            }
//        case 'S':
//            sigp++;
//            return syms.shortType;
//        case 'V':
//            sigp++;
//            return syms.voidType;
//        case 'Z':
//            sigp++;
//            return syms.booleanType;
//        case '[':
//            sigp++;
//            return new ArrayType(sigToType(), syms.arrayClass);
//        case '(':
//            sigp++;
//            List<Type> argtypes = sigToTypes(')');
//            Type restype = sigToType();
//            List<Type> thrown = List.nil();
//
//			DEBUG.P("signature[sigp]="+(char)signature[sigp]);
//            while (signature[sigp] == '^') {
//                sigp++;
//                thrown = thrown.prepend(sigToType());
//            }
//            return new MethodType(argtypes,
//                                  restype,
//                                  thrown.reverse(),
//                                  syms.methodClass);
//        case '<':
//			DEBUG.P("typevars="+typevars);
//            typevars = typevars.dup(currentOwner);
//            Type poly = new ForAll(sigToTypeParams(), sigToType());
//            typevars = typevars.leave();
//			DEBUG.P("typevars="+typevars);
//            return poly;
//        default:
//            throw badClassFile("bad.signature",
//                               Convert.utf2string(signature, sigp, 10));
//        }
//
//		}finally{//我加上的
//		DEBUG.P(0,this,"sigToType()");
//		}
//    }
//
//    byte[] signatureBuffer = new byte[0];
//    int sbp = 0;
//    /** Convert class signature to type, where signature is implicit.
//     */
//    Type classSigToType() {
//		try {//我加上的
//		DEBUG.P(this,"classSigToType()");
//		DEBUG.P("signature[sigp]="+(char)signature[sigp]);
//
//        if (signature[sigp] != 'L')
//            throw badClassFile("bad.class.signature",
//                               Convert.utf2string(signature, sigp, 10));
//        sigp++;
//        Type outer = Type.noType;
//        int startSbp = sbp;
//
//        while (true) {
//            final byte c = signature[sigp++];
//			DEBUG.P("c="+(char)c);
//
//            switch (c) {
//
//            case ';': {         // end
//                ClassSymbol t = enterClass(names.fromUtf(signatureBuffer,
//                                                         startSbp,
//                                                         sbp - startSbp));
//				DEBUG.P("outer="+outer);
//                if (outer == Type.noType)
//                    outer = t.erasure(types);
//                else
//                    outer = new ClassType(outer, List.<Type>nil(), t);
//                sbp = startSbp;
//				DEBUG.P("outer="+outer);
//                return outer;
//            }
//
//            case '<':           // generic arguments
//                // <editor-fold defaultstate="collapsed">
//                ClassSymbol t = enterClass(names.fromUtf(signatureBuffer,
//                                                         startSbp,
//                                                         sbp - startSbp));
//                outer = new ClassType(outer, sigToTypes('>'), t) {
//                        boolean completed = false;
//                        public Type getEnclosingType() {
//                            if (!completed) {
//                                completed = true;
//                                tsym.complete();
//                                Type enclosingType = tsym.type.getEnclosingType();
//                                if (enclosingType != Type.noType) {
//                                    List<Type> typeArgs =
//                                        super.getEnclosingType().allparams();
//                                    List<Type> typeParams =
//                                        enclosingType.allparams();
//                                    if (typeParams.length() != typeArgs.length()) {
//                                        // no "rare" types
//                                        super.setEnclosingType(types.erasure(enclosingType));
//                                    } else {
//                                        super.setEnclosingType(types.subst(enclosingType,
//                                                                           typeParams,
//                                                                           typeArgs));
//                                    }
//                                } else {
//                                    super.setEnclosingType(Type.noType);
//                                }
//                            }
//                            return super.getEnclosingType();
//                        }
//                        public void setEnclosingType(Type outer) {
//                            throw new UnsupportedOperationException();
//                        }
//                    };
//				DEBUG.P("signature[sigp++]="+(char)signature[sigp+1]);
//                switch (signature[sigp++]) {
//                case ';':
//
//					DEBUG.P("sigp="+sigp);
//					DEBUG.P("signature.length="+signature.length);
//					if(sigp < siglimit)
//						DEBUG.P("signature[sigp]="+(char)signature[sigp]);
//                    if (sigp < signature.length && signature[sigp] == '.') {
//                        // support old-style GJC signatures
//                        // The signature produced was
//                        // Lfoo/Outer<Lfoo/X;>;.Lfoo/Outer$Inner<Lfoo/Y;>;
//                        // rather than say
//                        // Lfoo/Outer<Lfoo/X;>.Inner<Lfoo/Y;>;
//                        // so we skip past ".Lfoo/Outer$"
//                        sigp += (sbp - startSbp) + // "foo/Outer"
//                            3;  // ".L" and "$"
//                        signatureBuffer[sbp++] = (byte)'$';
//                        break;
//                    } else {
//                        sbp = startSbp;
//                        return outer;
//                    }
//                case '.':
//                    signatureBuffer[sbp++] = (byte)'$';
//                    break;
//                default:
//                    throw new AssertionError(signature[sigp-1]);
//                }
//                
//                // </editor-fold>
//                continue;
//            case '.':
//                signatureBuffer[sbp++] = (byte)'$';
//                continue;
//            case '/':
//                signatureBuffer[sbp++] = (byte)'.';
//                continue;
//            default:
//                signatureBuffer[sbp++] = c;
//                continue;
//            }
//        }
//        
//        }finally{//我加上的
//        DEBUG.P(0,this,"classSigToType(1)");
//        }
//    }
//
//    /** Convert (implicit) signature to list of types
//     *  until `terminator' is encountered.
//     */
//    List<Type> sigToTypes(char terminator) {
//		DEBUG.P(this,"sigToTypes(1)");
//		DEBUG.P("terminator="+terminator);
//		
//        List<Type> head = List.of(null);
//        List<Type> tail = head;
//        while (signature[sigp] != terminator)
//            tail = tail.setTail(List.of(sigToType()));
//        sigp++;
//        
//        DEBUG.P("head.tail="+head.tail);
//        DEBUG.P(0,this,"sigToTypes(1)");
//        return head.tail;
//    }
//
//    /** Convert signature to type parameters, where signature is a name.
//     */
//    List<Type> sigToTypeParams(Name name) {
//    	try {//我加上的
//		DEBUG.P(this,"sigToTypeParams(1)");
//		DEBUG.P("name="+name);
//
//        return sigToTypeParams(name.table.names, name.index, name.len);
//        
//        }finally{//我加上的
//		DEBUG.P(0,this,"sigToTypeParams(1)");
//		}
//    }
//
//    /** Convert signature to type parameters, where signature is a byte
//     *  array segment.
//     */
//    List<Type> sigToTypeParams(byte[] sig, int offset, int len) {
//    	try {//我加上的
//		DEBUG.P(this,"sigToTypeParams(3)");
//		DEBUG.P("offset="+offset);
//		DEBUG.P("len="+len);
//		
//        signature = sig;
//        sigp = offset;
//        siglimit = offset + len;
//        return sigToTypeParams();
//        
//        }finally{//我加上的
//		DEBUG.P(0,this,"sigToTypeParams(3)");
//		}
//    }
//
//    /** Convert signature to type parameters, where signature is implicit.
//     */
//    List<Type> sigToTypeParams() {
//    	DEBUG.P(this,"sigToTypeParams()");
//		DEBUG.P("signature[sigp]="+(char)signature[sigp]);
//    	
//        List<Type> tvars = List.nil();
//        if (signature[sigp] == '<') {
//            sigp++;
//            int start = sigp;
//            sigEnterPhase = true;
//            while (signature[sigp] != '>')
//                tvars = tvars.prepend(sigToTypeParam());
//            sigEnterPhase = false;
//            sigp = start;
//
//			DEBUG.P("signature[sigp]="+(char)signature[sigp]);
//            while (signature[sigp] != '>')
//                sigToTypeParam();
//            sigp++;
//        }
//        
//        DEBUG.P("tvars.reverse()="+tvars.reverse());
//        DEBUG.P(0,this,"sigToTypeParams()");
//        return tvars.reverse();
//    }
//
//    /** Convert (implicit) signature to type parameter.
//     */
//    Type sigToTypeParam() {
//    	DEBUG.P(this,"sigToTypeParam()");
//    	DEBUG.P("sigp="+sigp);
//    	DEBUG.P("signature[sigp]="+(char)signature[sigp]);
//    	
//        int start = sigp;
//        while (signature[sigp] != ':') sigp++;
//        Name name = names.fromUtf(signature, start, sigp - start);
//        TypeVar tvar;
//		DEBUG.P("name="+name);
//		DEBUG.P("sigEnterPhase="+sigEnterPhase);
//		DEBUG.P("currentOwner="+currentOwner);
//        if (sigEnterPhase) {
//            tvar = new TypeVar(name, currentOwner);
//            typevars.enter(tvar.tsym);
//        } else {
//            tvar = (TypeVar)findTypeVar(name);
//        }
//		DEBUG.P("tvar="+tvar);
//        List<Type> bounds = List.nil();
//        Type st = null;
//
//		DEBUG.P("signature[sigp]="+(char)signature[sigp]);
//		if(signature[sigp] == ':')
//			DEBUG.P("signature[sigp+1]="+(char)signature[sigp+1]);
//
//        if (signature[sigp] == ':' && signature[sigp+1] == ':') {
//            sigp++;
//            st = syms.objectType;
//        }
//
//		DEBUG.P("st="+st);
//		DEBUG.P("signature[sigp]="+(char)signature[sigp]);
//        while (signature[sigp] == ':') {
//            sigp++;
//            bounds = bounds.prepend(sigToType());
//        }
//
//		DEBUG.P("sigEnterPhase="+sigEnterPhase);
//        if (!sigEnterPhase) {
//            types.setBounds(tvar, bounds.reverse(), st);
//        }
//        
//        DEBUG.P("tvar="+tvar);
//        DEBUG.P(0,this,"sigToTypeParam()");
//        return tvar;
//    }
//
//    /** Find type variable with given name in `typevars' scope.
//     */
//    Type findTypeVar(Name name) {
//        Scope.Entry e = typevars.lookup(name);
//        if (e.scope != null) {
//            return e.sym.type;
//        } else {
//            if (readingClassAttr) {
//                // While reading the class attribute, the supertypes
//                // might refer to a type variable from an enclosing element
//                // (method or class).
//                // If the type variable is defined in the enclosing class,
//                // we can actually find it in
//                // currentOwner.owner.type.getTypeArguments()
//                // However, until we have read the enclosing method attribute
//                // we don't know for sure if this owner is correct.  It could
//                // be a method and there is no way to tell before reading the
//                // enclosing method attribute.
//                TypeVar t = new TypeVar(name, currentOwner);
//                missingTypeVariables = missingTypeVariables.prepend(t);
//                // System.err.println("Missing type var " + name);
//                return t;
//            }
//            throw badClassFile("undecl.type.var", name);
//        }
//    }
//
///************************************************************************
// * Reading Attributes
// ***********************************************************************/
//
//    /** Report unrecognized attribute.
//     */
//    void unrecognized(Name attrName) {
//        if (checkClassFile)
//            printCCF("ccf.unrecognized.attribute", attrName);
//    }
//
//    /** Read member attribute.
//     */
//    void readMemberAttr(Symbol sym, Name attrName, int attrLen) {
//		DEBUG.P(this,"readMemberAttr(3)");
//		DEBUG.P("sym="+sym);
//		DEBUG.P("attrName="+attrName);
//		DEBUG.P("attrLen="+attrLen);
//
//        //- System.err.println(" z " + sym + ", " + attrName + ", " + attrLen);
//        if (attrName == names.ConstantValue) {
//            Object v = readPool(nextChar());
//            // Ignore ConstantValue attribute if field not final.
//            if ((sym.flags() & FINAL) != 0)
//                ((VarSymbol)sym).setData(v);
//        } else if (attrName == names.Code) {
//            if (readAllOfClassFile || saveParameterNames)
//                ((MethodSymbol)sym).code = readCode(sym);
//            else
//                bp = bp + attrLen;
//        } else if (attrName == names.Exceptions) {
//            int nexceptions = nextChar();
//            List<Type> thrown = List.nil();
//            for (int j = 0; j < nexceptions; j++)
//                thrown = thrown.prepend(readClassSymbol(nextChar()).type);
//            if (sym.type.getThrownTypes().isEmpty())
//                sym.type.asMethodType().thrown = thrown.reverse();
//        } else if (attrName == names.Synthetic) {
//            // bridge methods are visible when generics not enabled
//            if (allowGenerics || (sym.flags_field & BRIDGE) == 0)
//                sym.flags_field |= SYNTHETIC;
//        } else if (attrName == names.Bridge) {
//            sym.flags_field |= BRIDGE;
//            if (!allowGenerics)
//                sym.flags_field &= ~SYNTHETIC;
//        } else if (attrName == names.Deprecated) {
//            sym.flags_field |= DEPRECATED;
//        } else if (attrName == names.Varargs) {
//            if (allowVarargs) sym.flags_field |= VARARGS;
//        } else if (attrName == names.Annotation) {
//            if (allowAnnotations) sym.flags_field |= ANNOTATION;
//        } else if (attrName == names.Enum) {
//            sym.flags_field |= ENUM;
//        } else if (allowGenerics && attrName == names.Signature) {
//            List<Type> thrown = sym.type.getThrownTypes();
//            sym.type = readType(nextChar());
//            //- System.err.println(" # " + sym.type);
//            if (sym.kind == MTH && sym.type.getThrownTypes().isEmpty())
//                sym.type.asMethodType().thrown = thrown;
//        } else if (attrName == names.RuntimeVisibleAnnotations) {
//            attachAnnotations(sym);
//        } else if (attrName == names.RuntimeInvisibleAnnotations) {
//            attachAnnotations(sym);
//        } else if (attrName == names.RuntimeVisibleParameterAnnotations) {
//            attachParameterAnnotations(sym);
//        } else if (attrName == names.RuntimeInvisibleParameterAnnotations) {
//            attachParameterAnnotations(sym);
//        } else if (attrName == names.LocalVariableTable) {
//            int newbp = bp + attrLen;
//            if (saveParameterNames) {
//                // pick up parameter names from the variable table
//                List<Name> parameterNames = List.nil();
//                int firstParam = ((sym.flags() & STATIC) == 0) ? 1 : 0;
//                int endParam = firstParam + Code.width(sym.type.getParameterTypes());
//                int numEntries = nextChar();
//                for (int i=0; i<numEntries; i++) {
//                    int start_pc = nextChar();
//                    int length = nextChar();
//                    int nameIndex = nextChar();
//                    int sigIndex = nextChar();
//                    int register = nextChar();
//                    if (start_pc == 0 &&
//                        firstParam <= register &&
//                        register < endParam) {
//                        int index = firstParam;
//                        for (Type t : sym.type.getParameterTypes()) {
//                            if (index == register) {
//                                parameterNames = parameterNames.prepend(readName(nameIndex));
//                                break;
//                            }
//                            index += Code.width(t);
//                        }
//                    }
//                }
//                parameterNames = parameterNames.reverse();
//                ((MethodSymbol)sym).savedParameterNames = parameterNames;
//            }
//            bp = newbp;
//        } else if (attrName == names.AnnotationDefault) {
//            attachAnnotationDefault(sym);
//        } else if (attrName == names.EnclosingMethod) {
//            int newbp = bp + attrLen;
//            readEnclosingMethodAttr(sym);
//            bp = newbp;
//        } else {
//            unrecognized(attrName);
//            bp = bp + attrLen;
//        }
//
//		DEBUG.P(0,this,"readMemberAttr(3)");
//    }
//
//    void readEnclosingMethodAttr(Symbol sym) {
//        // sym is a nested class with an "Enclosing Method" attribute
//        // remove sym from it's current owners scope and place it in
//        // the scope specified by the attribute
//        sym.owner.members().remove(sym);
//        ClassSymbol self = (ClassSymbol)sym;
//        ClassSymbol c = readClassSymbol(nextChar());
//        NameAndType nt = (NameAndType)readPool(nextChar());
//
//        MethodSymbol m = findMethod(nt, c.members_field, self.flags());
//        if (nt != null && m == null)
//            throw badClassFile("bad.enclosing.method", self);
//
//        self.name = simpleBinaryName(self.flatname, c.flatname) ;
//        self.owner = m != null ? m : c;
//        if (self.name.len == 0)
//            self.fullname = null;
//        else
//            self.fullname = ClassSymbol.formFullName(self.name, self.owner);
//
//        if (m != null) {
//            ((ClassType)sym.type).setEnclosingType(m.type);
//        } else if ((self.flags_field & STATIC) == 0) {
//            ((ClassType)sym.type).setEnclosingType(c.type);
//        } else {
//            ((ClassType)sym.type).setEnclosingType(Type.noType);
//        }
//        enterTypevars(self);
//        if (!missingTypeVariables.isEmpty()) {
//            ListBuffer<Type> typeVars =  new ListBuffer<Type>();
//            for (Type typevar : missingTypeVariables) {
//                typeVars.append(findTypeVar(typevar.tsym.name));
//            }
//            foundTypeVariables = typeVars.toList();
//        } else {
//            foundTypeVariables = List.nil();
//        }
//    }
//
//    // See java.lang.Class
//    private Name simpleBinaryName(Name self, Name enclosing) {
//        String simpleBinaryName = self.toString().substring(enclosing.toString().length());
//        if (simpleBinaryName.length() < 1 || simpleBinaryName.charAt(0) != '$')
//            throw badClassFile("bad.enclosing.method", self);
//        int index = 1;
//        while (index < simpleBinaryName.length() &&
//               isAsciiDigit(simpleBinaryName.charAt(index)))
//            index++;
//        return names.fromString(simpleBinaryName.substring(index));
//    }
//
//    private MethodSymbol findMethod(NameAndType nt, Scope scope, long flags) {
//        if (nt == null)
//            return null;
//
//        MethodType type = nt.type.asMethodType();
//
//        for (Scope.Entry e = scope.lookup(nt.name); e.scope != null; e = e.next())
//            if (e.sym.kind == MTH && isSameBinaryType(e.sym.type.asMethodType(), type))
//                return (MethodSymbol)e.sym;
//
//        if (nt.name != names.init)
//            // not a constructor
//            return null;
//        if ((flags & INTERFACE) != 0)
//            // no enclosing instance
//            return null;
//        if (nt.type.getParameterTypes().isEmpty())
//            // no parameters
//            return null;
//
//        // A constructor of an inner class.
//        // Remove the first argument (the enclosing instance)
//        nt.type = new MethodType(nt.type.getParameterTypes().tail,
//                                 nt.type.getReturnType(),
//                                 nt.type.getThrownTypes(),
//                                 syms.methodClass);
//        // Try searching again
//        return findMethod(nt, scope, flags);
//    }
//
//    /** Similar to Types.isSameType but avoids completion */
//    private boolean isSameBinaryType(MethodType mt1, MethodType mt2) {
//        List<Type> types1 = types.erasure(mt1.getParameterTypes())
//            .prepend(types.erasure(mt1.getReturnType()));
//        List<Type> types2 = mt2.getParameterTypes().prepend(mt2.getReturnType());
//        while (!types1.isEmpty() && !types2.isEmpty()) {
//            if (types1.head.tsym != types2.head.tsym)
//                return false;
//            types1 = types1.tail;
//            types2 = types2.tail;
//        }
//        return types1.isEmpty() && types2.isEmpty();
//    }
//
//    /**
//     * Character.isDigit answers <tt>true</tt> to some non-ascii
//     * digits.  This one does not.  <b>copied from java.lang.Class</b>
//     */
//    private static boolean isAsciiDigit(char c) {
//        return '0' <= c && c <= '9';
//    }
//
//    /** Read member attributes.
//     */
//    void readMemberAttrs(Symbol sym) {
//    	DEBUG.P(this,"readMemberAttrs(1)");
//		DEBUG.P("sym="+sym);
//		
//        char ac = nextChar();
//        
//        DEBUG.P("ac="+(int)ac);
//        
//        for (int i = 0; i < ac; i++) {
//            Name attrName = readName(nextChar());
//            int attrLen = nextInt();
//            readMemberAttr(sym, attrName, attrLen);
//        }
//        
//        DEBUG.P(0,this,"readMemberAttrs(1)");
//    }
//
//    /** Read class attribute.
//     */
//    void readClassAttr(ClassSymbol c, Name attrName, int attrLen) {
//    	DEBUG.P(this,"readClassAttr(3)");
//		DEBUG.P("c="+c);
//		DEBUG.P("attrName="+attrName);
//		DEBUG.P("attrLen="+attrLen);
//		
//        if (attrName == names.SourceFile) {
//            Name n = readName(nextChar());
//            c.sourcefile = new SourceFileObject(n);
//        } else if (attrName == names.InnerClasses) {
//            readInnerClasses(c);
//        } else if (allowGenerics && attrName == names.Signature) {
//            //属性Signature的格式是:
//            //u2 字符串"Signature"在常量池中的索引
//            //u4 属性长度
//            //u2 属性值在常量池中的索引
//            readingClassAttr = true;
//            try {
//                ClassType ct1 = (ClassType)c.type;
//                assert c == currentOwner;
//                ct1.typarams_field = readTypeParams(nextChar());
//                //DEBUG.P("ct1.typarams_field="+ct1.typarams_field);
//                
//                ct1.supertype_field = sigToType();
//
//				//这一句有Bug
//				/*
//				java.lang.ClassCastException: com.sun.tools.javac.code.Symbol$ClassSymbol cannot
// be cast to com.sun.tools.javac.util.Name
//        at com.sun.tools.javac.jvm.ClassReader.readName(ClassReader.java:552)
//        at com.sun.tools.javac.jvm.ClassReader.readClassAttrs(ClassReader.java:1
//160)*/
//                //DEBUG.P("ct1.supertype_field="+ct1.supertype_field);
//                
//                ListBuffer<Type> is = new ListBuffer<Type>();
//                while (sigp != siglimit) is.append(sigToType());
//                ct1.interfaces_field = is.toList();
//                //DEBUG.P("ct1.interfaces_field="+ct1.interfaces_field);
//            } finally {
//                readingClassAttr = false;
//            }
//        } else {
//            readMemberAttr(c, attrName, attrLen);
//        }
//        
//        DEBUG.P(0,this,"readClassAttr(3)");
//    }
//    private boolean readingClassAttr = false;
//    private List<Type> missingTypeVariables = List.nil();
//    private List<Type> foundTypeVariables = List.nil();
//
//    /** Read class attributes.
//     */
//    void readClassAttrs(ClassSymbol c) {
//		DEBUG.P(this,"readClassAttrs(1)");
//		DEBUG.P("c="+c);
//
//        char ac = nextChar();//类属性总个数
//		DEBUG.P("ac="+(int)ac);
//        for (int i = 0; i < ac; i++) {
//            Name attrName = readName(nextChar());//属性名称常量池索引
//			DEBUG.P("attrName="+attrName);
//            int attrLen = nextInt();
//            readClassAttr(c, attrName, attrLen);
//        }
//
//		DEBUG.P(0,this,"readClassAttrs(1)");
//    }
//
//    /** Read code block.
//     */
//    Code readCode(Symbol owner) {
//        nextChar(); // max_stack
//        nextChar(); // max_locals
//        final int  code_length = nextInt();
//        bp += code_length;
//        final char exception_table_length = nextChar();
//        bp += exception_table_length * 8;
//        readMemberAttrs(owner);
//        return null;
//    }
//
///************************************************************************
// * Reading Java-language annotations
// ***********************************************************************/
//
//    /** Attach annotations.
//     */
//    void attachAnnotations(final Symbol sym) {
//        int numAttributes = nextChar();
//        if (numAttributes != 0) {
//            ListBuffer<CompoundAnnotationProxy> proxies =
//                new ListBuffer<CompoundAnnotationProxy>();
//            for (int i = 0; i<numAttributes; i++) {
//                CompoundAnnotationProxy proxy = readCompoundAnnotation();
//                if (proxy.type.tsym == syms.proprietaryType.tsym)
//                    sym.flags_field |= PROPRIETARY;
//                else
//                    proxies.append(proxy);
//            }
//            annotate.later(new AnnotationCompleter(sym, proxies.toList()));
//        }
//    }
//
//    /** Attach parameter annotations.
//     */
//    void attachParameterAnnotations(final Symbol method) {
//        final MethodSymbol meth = (MethodSymbol)method;
//        int numParameters = buf[bp++] & 0xFF;
//        List<VarSymbol> parameters = meth.params();
//        int pnum = 0;
//        while (parameters.tail != null) {
//            attachAnnotations(parameters.head);
//            parameters = parameters.tail;
//            pnum++;
//        }
//        if (pnum != numParameters) {
//            throw badClassFile("bad.runtime.invisible.param.annotations", meth);
//        }
//    }
//
//    /** Attach the default value for an annotation element.
//     */
//    void attachAnnotationDefault(final Symbol sym) {
//        final MethodSymbol meth = (MethodSymbol)sym; // only on methods
//        final Attribute value = readAttributeValue();
//        annotate.later(new AnnotationDefaultCompleter(meth, value));
//    }
//
//    Type readTypeOrClassSymbol(int i) {
//        // support preliminary jsr175-format class files
//        if (buf[poolIdx[i]] == CONSTANT_Class)
//            return readClassSymbol(i).type;
//        return readType(i);
//    }
//    Type readEnumType(int i) {
//        // support preliminary jsr175-format class files
//        int index = poolIdx[i];
//        int length = getChar(index + 1);
//        if (buf[index + length + 2] != ';')
//            return enterClass(readName(i)).type;
//        return readType(i);
//    }
//
//    CompoundAnnotationProxy readCompoundAnnotation() {
//        Type t = readTypeOrClassSymbol(nextChar());
//        int numFields = nextChar();
//        ListBuffer<Pair<Name,Attribute>> pairs =
//            new ListBuffer<Pair<Name,Attribute>>();
//        for (int i=0; i<numFields; i++) {
//            Name name = readName(nextChar());
//            Attribute value = readAttributeValue();
//            pairs.append(new Pair<Name,Attribute>(name, value));
//        }
//        return new CompoundAnnotationProxy(t, pairs.toList());
//    }
//
//    Attribute readAttributeValue() {
//        char c = (char) buf[bp++];
//        switch (c) {
//        case 'B':
//            return new Attribute.Constant(syms.byteType, readPool(nextChar()));
//        case 'C':
//            return new Attribute.Constant(syms.charType, readPool(nextChar()));
//        case 'D':
//            return new Attribute.Constant(syms.doubleType, readPool(nextChar()));
//        case 'F':
//            return new Attribute.Constant(syms.floatType, readPool(nextChar()));
//        case 'I':
//            return new Attribute.Constant(syms.intType, readPool(nextChar()));
//        case 'J':
//            return new Attribute.Constant(syms.longType, readPool(nextChar()));
//        case 'S':
//            return new Attribute.Constant(syms.shortType, readPool(nextChar()));
//        case 'Z':
//            return new Attribute.Constant(syms.booleanType, readPool(nextChar()));
//        case 's':
//            return new Attribute.Constant(syms.stringType, readPool(nextChar()).toString());
//        case 'e':
//            return new EnumAttributeProxy(readEnumType(nextChar()), readName(nextChar()));
//        case 'c':
//            return new Attribute.Class(types, readTypeOrClassSymbol(nextChar()));
//        case '[': {
//            int n = nextChar();
//            ListBuffer<Attribute> l = new ListBuffer<Attribute>();
//            for (int i=0; i<n; i++)
//                l.append(readAttributeValue());
//            return new ArrayAttributeProxy(l.toList());
//        }
//        case '@':
//            return readCompoundAnnotation();
//        default:
//            throw new AssertionError("unknown annotation tag '" + c + "'");
//        }
//    }
//
//    interface ProxyVisitor extends Attribute.Visitor {
//        void visitEnumAttributeProxy(EnumAttributeProxy proxy);
//        void visitArrayAttributeProxy(ArrayAttributeProxy proxy);
//        void visitCompoundAnnotationProxy(CompoundAnnotationProxy proxy);
//    }
//
//    static class EnumAttributeProxy extends Attribute {
//        Type enumType;
//        Name enumerator;
//        public EnumAttributeProxy(Type enumType, Name enumerator) {
//            super(null);
//            this.enumType = enumType;
//            this.enumerator = enumerator;
//        }
//        public void accept(Visitor v) { ((ProxyVisitor)v).visitEnumAttributeProxy(this); }
//        public String toString() {
//            return "/*proxy enum*/" + enumType + "." + enumerator;
//        }
//    }
//
//    static class ArrayAttributeProxy extends Attribute {
//        List<Attribute> values;
//        ArrayAttributeProxy(List<Attribute> values) {
//            super(null);
//            this.values = values;
//        }
//        public void accept(Visitor v) { ((ProxyVisitor)v).visitArrayAttributeProxy(this); }
//        public String toString() {
//            return "{" + values + "}";
//        }
//    }
//
//    /** A temporary proxy representing a compound attribute.
//     */
//    static class CompoundAnnotationProxy extends Attribute {
//        final List<Pair<Name,Attribute>> values;
//        public CompoundAnnotationProxy(Type type,
//                                      List<Pair<Name,Attribute>> values) {
//            super(type);
//            this.values = values;
//        }
//        public void accept(Visitor v) { ((ProxyVisitor)v).visitCompoundAnnotationProxy(this); }
//        public String toString() {
//            StringBuffer buf = new StringBuffer();
//            buf.append("@");
//            buf.append(type.tsym.getQualifiedName());
//            buf.append("/*proxy*/{");
//            boolean first = true;
//            for (List<Pair<Name,Attribute>> v = values;
//                 v.nonEmpty(); v = v.tail) {
//                Pair<Name,Attribute> value = v.head;
//                if (!first) buf.append(",");
//                first = false;
//                buf.append(value.fst);
//                buf.append("=");
//                buf.append(value.snd);
//            }
//            buf.append("}");
//            return buf.toString();
//        }
//    }
//
//    class AnnotationDeproxy implements ProxyVisitor {
//        private ClassSymbol requestingOwner = currentOwner.kind == MTH
//            ? currentOwner.enclClass() : (ClassSymbol)currentOwner;
//
//        List<Attribute.Compound> deproxyCompoundList(List<CompoundAnnotationProxy> pl) {
//            // also must fill in types!!!!
//            ListBuffer<Attribute.Compound> buf =
//                new ListBuffer<Attribute.Compound>();
//            for (List<CompoundAnnotationProxy> l = pl; l.nonEmpty(); l=l.tail) {
//                buf.append(deproxyCompound(l.head));
//            }
//            return buf.toList();
//        }
//
//        Attribute.Compound deproxyCompound(CompoundAnnotationProxy a) {
//            ListBuffer<Pair<Symbol.MethodSymbol,Attribute>> buf =
//                new ListBuffer<Pair<Symbol.MethodSymbol,Attribute>>();
//            for (List<Pair<Name,Attribute>> l = a.values;
//                 l.nonEmpty();
//                 l = l.tail) {
//                MethodSymbol meth = findAccessMethod(a.type, l.head.fst);
//                buf.append(new Pair<Symbol.MethodSymbol,Attribute>
//                           (meth, deproxy(meth.type.getReturnType(), l.head.snd)));
//            }
//            return new Attribute.Compound(a.type, buf.toList());
//        }
//
//        MethodSymbol findAccessMethod(Type container, Name name) {
//            CompletionFailure failure = null;
//            try {
//                for (Scope.Entry e = container.tsym.members().lookup(name);
//                     e.scope != null;
//                     e = e.next()) {
//                    Symbol sym = e.sym;
//                    if (sym.kind == MTH && sym.type.getParameterTypes().length() == 0)
//                        return (MethodSymbol) sym;
//                }
//            } catch (CompletionFailure ex) {
//                failure = ex;
//            }
//            // The method wasn't found: emit a warning and recover
//            JavaFileObject prevSource = log.useSource(requestingOwner.classfile);
//            try {
//                if (failure == null) {
//                    log.warning("annotation.method.not.found",
//                                container,
//                                name);
//                } else {
//                    log.warning("annotation.method.not.found.reason",
//                                container,
//                                name,
//                                failure.getMessage());
//                }
//            } finally {
//                log.useSource(prevSource);
//            }
//            // Construct a new method type and symbol.  Use bottom
//            // type (typeof null) as return type because this type is
//            // a subtype of all reference types and can be converted
//            // to primitive types by unboxing.
//            MethodType mt = new MethodType(List.<Type>nil(),
//                                           syms.botType,
//                                           List.<Type>nil(),
//                                           syms.methodClass);
//            return new MethodSymbol(PUBLIC | ABSTRACT, name, mt, container.tsym);
//        }
//
//        Attribute result;
//        Type type;
//        Attribute deproxy(Type t, Attribute a) {
//            Type oldType = type;
//            try {
//                type = t;
//                a.accept(this);
//                return result;
//            } finally {
//                type = oldType;
//            }
//        }
//
//        // implement Attribute.Visitor below
//
//        public void visitConstant(Attribute.Constant value) {
//            // assert value.type == type;
//            result = value;
//        }
//
//        public void visitClass(Attribute.Class clazz) {
//            result = clazz;
//        }
//
//        public void visitEnum(Attribute.Enum e) {
//            throw new AssertionError(); // shouldn't happen
//        }
//
//        public void visitCompound(Attribute.Compound compound) {
//            throw new AssertionError(); // shouldn't happen
//        }
//
//        public void visitArray(Attribute.Array array) {
//            throw new AssertionError(); // shouldn't happen
//        }
//
//        public void visitError(Attribute.Error e) {
//            throw new AssertionError(); // shouldn't happen
//        }
//
//        public void visitEnumAttributeProxy(EnumAttributeProxy proxy) {
//            // type.tsym.flatName() should == proxy.enumFlatName
//            TypeSymbol enumTypeSym = proxy.enumType.tsym;
//            VarSymbol enumerator = null;
//            for (Scope.Entry e = enumTypeSym.members().lookup(proxy.enumerator);
//                 e.scope != null;
//                 e = e.next()) {
//                if (e.sym.kind == VAR) {
//                    enumerator = (VarSymbol)e.sym;
//                    break;
//                }
//            }
//            if (enumerator == null) {
//                log.error("unknown.enum.constant",
//                          currentClassFile, enumTypeSym, proxy.enumerator);
//                result = new Attribute.Error(enumTypeSym.type);
//            } else {
//                result = new Attribute.Enum(enumTypeSym.type, enumerator);
//            }
//        }
//
//        public void visitArrayAttributeProxy(ArrayAttributeProxy proxy) {
//            int length = proxy.values.length();
//            Attribute[] ats = new Attribute[length];
//            Type elemtype = types.elemtype(type);
//            int i = 0;
//            for (List<Attribute> p = proxy.values; p.nonEmpty(); p = p.tail) {
//                ats[i++] = deproxy(elemtype, p.head);
//            }
//            result = new Attribute.Array(type, ats);
//        }
//
//        public void visitCompoundAnnotationProxy(CompoundAnnotationProxy proxy) {
//            result = deproxyCompound(proxy);
//        }
//    }
//
//    class AnnotationDefaultCompleter extends AnnotationDeproxy implements Annotate.Annotator {
//        final MethodSymbol sym;
//        final Attribute value;
//        final JavaFileObject classFile = currentClassFile;
//        public String toString() {
//            return " ClassReader store default for " + sym.owner + "." + sym + " is " + value;
//        }
//        AnnotationDefaultCompleter(MethodSymbol sym, Attribute value) {
//            this.sym = sym;
//            this.value = value;
//        }
//        // implement Annotate.Annotator.enterAnnotation()
//        public void enterAnnotation() {
//            JavaFileObject previousClassFile = currentClassFile;
//            try {
//                currentClassFile = classFile;
//                sym.defaultValue = deproxy(sym.type.getReturnType(), value);
//            } finally {
//                currentClassFile = previousClassFile;
//            }
//        }
//    }
//
//    class AnnotationCompleter extends AnnotationDeproxy implements Annotate.Annotator {
//        final Symbol sym;
//        final List<CompoundAnnotationProxy> l;
//        final JavaFileObject classFile;
//        public String toString() {
//            return " ClassReader annotate " + sym.owner + "." + sym + " with " + l;
//        }
//        AnnotationCompleter(Symbol sym, List<CompoundAnnotationProxy> l) {
//            this.sym = sym;
//            this.l = l;
//            this.classFile = currentClassFile;
//        }
//        // implement Annotate.Annotator.enterAnnotation()
//        public void enterAnnotation() {
//            JavaFileObject previousClassFile = currentClassFile;
//            try {
//                currentClassFile = classFile;
//                List<Attribute.Compound> newList = deproxyCompoundList(l);
//                sym.attributes_field = ((sym.attributes_field == null)
//                                        ? newList
//                                        : newList.prependList(sym.attributes_field));
//            } finally {
//                currentClassFile = previousClassFile;
//            }
//        }
//    }
//
//
///************************************************************************
// * Reading Symbols
// ***********************************************************************/
//
//    /** Read a field.
//     */
//    VarSymbol readField() {
//    	DEBUG.P(this,"readField()");
//
//        long flags = adjustFieldFlags(nextChar());
//        Name name = readName(nextChar());
//        Type type = readType(nextChar());
//        VarSymbol v = new VarSymbol(flags, name, type, currentOwner);
//        readMemberAttrs(v);
//        
//        DEBUG.P("v="+v);
//        DEBUG.P(0,this,"readField()");
//        return v;
//    }
//
//    /** Read a method.
//     */
//    MethodSymbol readMethod() {
//    	DEBUG.P(this,"readMethod()");
//    	
//        long flags = adjustMethodFlags(nextChar());
//        Name name = readName(nextChar());
//        Type type = readType(nextChar());
//        if (name == names.init && currentOwner.hasOuterInstance()) {
//            // Sometimes anonymous classes don't have an outer
//            // instance, however, there is no reliable way to tell so
//            // we never strip this$n
//            if (currentOwner.name.len != 0)
//                type = new MethodType(type.getParameterTypes().tail,
//                                      type.getReturnType(),
//                                      type.getThrownTypes(),
//                                      syms.methodClass);
//        }
//        MethodSymbol m = new MethodSymbol(flags, name, type, currentOwner);
//        Symbol prevOwner = currentOwner;
//        currentOwner = m;
//        try {
//            readMemberAttrs(m);
//        } finally {
//            currentOwner = prevOwner;
//        }
//        
//        DEBUG.P("m="+m);
//        DEBUG.P(0,this,"readMethod()");
//        return m;
//    }
//
//    /** Skip a field or method
//     */
//    void skipMember() {
//        bp = bp + 6;
//        char ac = nextChar();
//        for (int i = 0; i < ac; i++) {
//            bp = bp + 2;
//            int attrLen = nextInt();
//            bp = bp + attrLen;
//        }
//    }
//
//    /** Enter type variables of this classtype and all enclosing ones in
//     *  `typevars'.
//     */
//    protected void enterTypevars(Type t) {
//    	DEBUG.P(this,"enterTypevars(Type t)");
//		DEBUG.P("t="+t);
//		DEBUG.P("t.getEnclosingType()="+t.getEnclosingType());
//		DEBUG.P("t.getTypeArguments()="+t.getTypeArguments());
//		DEBUG.P("typevars="+typevars);
//		
//        if (t.getEnclosingType() != null && t.getEnclosingType().tag == CLASS)
//            enterTypevars(t.getEnclosingType());
//        for (List<Type> xs = t.getTypeArguments(); xs.nonEmpty(); xs = xs.tail)
//            typevars.enter(xs.head.tsym);
//        
//		DEBUG.P("typevars="+typevars);
//        DEBUG.P(0,this,"enterTypevars(Type t)");
//    }
//
//    protected void enterTypevars(Symbol sym) {
//        if (sym.owner.kind == MTH) {
//            enterTypevars(sym.owner);
//            enterTypevars(sym.owner.owner);
//        }
//        enterTypevars(sym.type);
//    }
//
//    /** Read contents of a given class symbol `c'. Both external and internal
//     *  versions of an inner class are read.
//     */
//    void readClass(ClassSymbol c) {
//    	try {//我加上的
//		DEBUG.P(this,"readClass(1)");
//
//        ClassType ct = (ClassType)c.type;
//
//        // allocate scope for members
//        c.members_field = new Scope(c);
//
//        // prepare type variable table
//        typevars = typevars.dup(currentOwner);
//        
//        DEBUG.P("c="+c);
//        DEBUG.P("ct="+ct);
//        DEBUG.P("c.members_field="+c.members_field);
//        DEBUG.P("currentOwner="+currentOwner);
//        DEBUG.P("typevars="+typevars);
//        DEBUG.P("ct.getEnclosingType()="+ct.getEnclosingType());
//        DEBUG.P("ct.getEnclosingType().tag="+TypeTags.toString(ct.getEnclosingType().tag));
//        
//        if (ct.getEnclosingType().tag == CLASS) enterTypevars(ct.getEnclosingType());
//
//        // read flags, or skip if this is an inner class
//        long flags = adjustClassFlags(nextChar());
//        DEBUG.P("");
//        DEBUG.P("flags="+Flags.toString(flags));
//        DEBUG.P("c.owner="+c.owner);
//        DEBUG.P("c.owner.kind="+Kinds.toString(c.owner.kind));
//        if (c.owner.kind == PCK) c.flags_field = flags;
//
//        // read own class name and check that it matches
//        ClassSymbol self = readClassSymbol(nextChar());
//        DEBUG.P("self="+self);
//        if (c != self)
//            throw badClassFile("class.file.wrong.class",
//                               self.flatname);
//
//        // class attributes must be read before class
//        // skip ahead to read class attributes
//        int startbp = bp;
//        nextChar();
//        char interfaceCount = nextChar();
//        bp += interfaceCount * 2;
//        char fieldCount = nextChar();
//        for (int i = 0; i < fieldCount; i++) skipMember();
//        char methodCount = nextChar();
//        for (int i = 0; i < methodCount; i++) skipMember();
//        readClassAttrs(c);
//
//		DEBUG.P("readAllOfClassFile="+readAllOfClassFile);
//        if (readAllOfClassFile) {
//            for (int i = 1; i < poolObj.length; i++) readPool(i);
//            c.pool = new Pool(poolObj.length, poolObj);
//        }
//
//        // reset and read rest of classinfo
//        bp = startbp;
//        int n = nextChar();
//		DEBUG.P("n="+n);
//		DEBUG.P("ct.supertype_field="+ct.supertype_field);
//        if (ct.supertype_field == null)
//            ct.supertype_field = (n == 0)
//                ? Type.noType
//                : readClassSymbol(n).erasure(types);
//		DEBUG.P("ct.supertype_field="+ct.supertype_field);
//        n = nextChar();
//        List<Type> is = List.nil();
//        for (int i = 0; i < n; i++) {
//            Type _inter = readClassSymbol(nextChar()).erasure(types);
//            is = is.prepend(_inter);
//        }
//        if (ct.interfaces_field == null)
//            ct.interfaces_field = is.reverse();
//
//        if (fieldCount != nextChar()) assert false;
//        for (int i = 0; i < fieldCount; i++) enterMember(c, readField());
//        if (methodCount != nextChar()) assert false;
//        for (int i = 0; i < methodCount; i++) enterMember(c, readMethod());
//
//        typevars = typevars.leave();
//        
//        }finally{//我加上的
//		DEBUG.P(0,this,"readClass(1)");
//		}
//    }
//
//    /** Read inner class info. For each inner/outer pair allocate a
//     *  member class.
//     */
//    void readInnerClasses(ClassSymbol c) {
//        int n = nextChar();
//        for (int i = 0; i < n; i++) {
//            nextChar(); // skip inner class symbol
//            ClassSymbol outer = readClassSymbol(nextChar());
//            Name name = readName(nextChar());
//            if (name == null) name = names.empty;
//            long flags = adjustClassFlags(nextChar());
//            if (outer != null) { // we have a member class
//                if (name == names.empty)
//                    name = names.one;
//                ClassSymbol member = enterClass(name, outer);
//                if ((flags & STATIC) == 0) {
//                    ((ClassType)member.type).setEnclosingType(outer.type);
//                    if (member.erasure_field != null)
//                        ((ClassType)member.erasure_field).setEnclosingType(types.erasure(outer.type));
//                }
//                if (c == outer) {
//                    member.flags_field = flags;
//                    enterMember(c, member);
//                }
//            }
//        }
//    }
//
//    /** Read a class file.
//     */
//    private void readClassFile(ClassSymbol c) throws IOException {
//    	try {//我加上的
//		DEBUG.P(this,"readClassFile(1)");
//		DEBUG.P("c="+c);
//
//        int magic = nextInt();
//        
//        DEBUG.P("magic="+magic+" JAVA_MAGIC="+JAVA_MAGIC);
//        
//        if (magic != JAVA_MAGIC)
//            throw badClassFile("illegal.start.of.class.file");
//
//        int minorVersion = nextChar();
//        int majorVersion = nextChar();
//        int maxMajor = Target.MAX().majorVersion;
//        int maxMinor = Target.MAX().minorVersion;
//        
//        DEBUG.P("minorVersion="+minorVersion+" majorVersion="+majorVersion);
//        DEBUG.P("maxMinor="+maxMinor+" maxMajor="+maxMajor);
//        DEBUG.P("bp="+bp);
//        DEBUG.P("checkClassFile="+checkClassFile);
//        
//        if (majorVersion > maxMajor ||
//            majorVersion * 1000 + minorVersion <
//            Target.MIN().majorVersion * 1000 + Target.MIN().minorVersion)
//        {
//            if (majorVersion == (maxMajor + 1)) 
//                log.warning("big.major.version",
//                            currentClassFile,
//                            majorVersion,
//                            maxMajor);
//            else
//                throw badClassFile("wrong.version",
//                                   Integer.toString(majorVersion),
//                                   Integer.toString(minorVersion),
//                                   Integer.toString(maxMajor),
//                                   Integer.toString(maxMinor));
//        }
//        else if (checkClassFile &&
//                 majorVersion == maxMajor &&
//                 minorVersion > maxMinor)
//        {
//        	//源码漏了"ccf"
//        	//printCCF("found.later.version",
//            //         Integer.toString(minorVersion));
//        	
//        	//我加上的
//            printCCF("ccf.found.later.version",
//                     Integer.toString(minorVersion));
//        }
//        indexPool();
//        DEBUG.P("bp="+bp);
//        DEBUG.P("signatureBuffer.length="+signatureBuffer.length);
//        if (signatureBuffer.length < bp) {
//        	//分析bp的值在1,2,4,8,16,32,64,128,256,512,1024,2048....这
//        	//样的数列中的位置，从中取一个>=bp的最小值做为signatureBuffer的
//        	//长度(这有点像内存条容量增加的方式，从一定程度有性能提升)
//        	//如bp=916，那么signatureBuffer大小为1024
//            int ns = Integer.highestOneBit(bp) << 1;
//            signatureBuffer = new byte[ns];
//        }
//        DEBUG.P("signatureBuffer.length="+signatureBuffer.length);
//        readClass(c);
//        
//        }finally{//我加上的
//		DEBUG.P(0,this,"readClassFile(1)");
//		}
//    }
//
///************************************************************************
// * Adjusting flags
// ***********************************************************************/
//
//    long adjustFieldFlags(long flags) {
//    	DEBUG.P(this,"adjustFieldFlags(1)");
//		DEBUG.P("flags="+Flags.toString(flags));
//		DEBUG.P(0,this,"adjustFieldFlags(1)");
//        return flags;
//    }
//    long adjustMethodFlags(long flags) {
//    	try {//我加上的
//		DEBUG.P(this,"adjustMethodFlags(1)");
//		DEBUG.P("flags="+Flags.toString(flags));
//
//        if ((flags & ACC_BRIDGE) != 0) {
//            flags &= ~ACC_BRIDGE;
//            flags |= BRIDGE;
//            if (!allowGenerics)
//                flags &= ~SYNTHETIC;
//        }
//        if ((flags & ACC_VARARGS) != 0) {
//            flags &= ~ACC_VARARGS;
//            flags |= VARARGS;
//        }
//        
//        DEBUG.P("flags="+Flags.toString(flags));
//        return flags;
//        
//        }finally{//我加上的
//		DEBUG.P(0,this,"adjustMethodFlags(1)");
//		}
//    }
//    long adjustClassFlags(long flags) {
//    	try {//我加上的
//		DEBUG.P(this,"adjustClassFlags((1)");
//		DEBUG.P("flags="+Flags.toString(flags));
//		
//        return flags & ~ACC_SUPER; // SUPER and SYNCHRONIZED bits overloaded
//        
//        }finally{//我加上的
//        DEBUG.P("flags="+Flags.toString(flags & ~ACC_SUPER));
//		DEBUG.P(0,this,"adjustMethodFlags(1)");
//		}
//    }
//
///************************************************************************
// * Loading Classes
// ***********************************************************************/
//
//    /** Define a new class given its name and owner.
//     */
//    public ClassSymbol defineClass(Name name, Symbol owner) {
//    	//DEBUG.P("defineClass(Name name="+name+", Symbol owner="+owner+")");
//        ClassSymbol c = new ClassSymbol(0, name, owner);
//        
//        //在ClassSymbol(0, name, owner)内部已按name和owner对flatname赋值
//        if (owner.kind == PCK)
//            assert classes.get(c.flatname) == null : c;//同一包下不能有同名的两个(或多个)类
//        c.completer = this;
//        DEBUG.P("新增ClassSymbol(name="+name+", owner="+owner+", flags=0, completer=ClassReader)");
//        return c;
//    }
//
//    /** Create a new toplevel or member class symbol with given name
//     *  and owner and enter in `classes' unless already there.
//     */
//    public ClassSymbol enterClass(Name name, TypeSymbol owner) {
//    	DEBUG.P(this,"enterClass(Name name, TypeSymbol owner)");
//    	DEBUG.P("name="+name+" owner="+owner);
//    	
//        Name flatname = TypeSymbol.formFlatName(name, owner);
//        ClassSymbol c = classes.get(flatname);
//        
//        DEBUG.P("flatname="+flatname+" ClassSymbol c="+c);
//        
//        if (c == null) {
//            c = defineClass(name, owner);
//            classes.put(flatname, c);
//        } else if ((c.name != name || c.owner != owner) && owner.kind == TYP && c.owner.kind == PCK) {
//        	/*
//        	这种情况主要是在一个类中又定义了一个类(或接口)(也就是成员类的情况)
//        	在执行Enter.visitTopLevel()方法时需要为JCCompilationUnit.packge.members_field
//        	加载包名目录下的所有类文件并“包装”成ClassSymbol加入members_field中，但在执行
//        	到Enter.visitClassDef()时成员类得重新移到它的owner的Scope中
//        	
//        	举例:如下代码片断:
//        	package my.test;
//        	public class Test {
//				public static interface MyInterface {
//				}
//			}
//			打印结果:
//			com.sun.tools.javac.jvm.ClassReader===>enterClass(Name name, TypeSymbol owner)
//			-------------------------------------------------------------------------
//			name=MyInterface owner=my.test.Test
//			flatname=my.test.Test$MyInterface ClassSymbol c=my.test.Test$MyInterface
//			c.name=Test$MyInterface c.owner=my.test
//			c.fullname(注意分析)=my.test.Test.MyInterface
//			com.sun.tools.javac.jvm.ClassReader===>enterClass(Name name, TypeSymbol owner)  END
//			-------------------------------------------------------------------------
//        	*/
//        	
//        	
//            // reassign fields of classes that might have been loaded with
//            // their flat names.
//            DEBUG.P("c.name="+c.name+" c.owner="+c.owner);
//            c.owner.members().remove(c);
//            DEBUG.P("("+name+")是一个成员类，已从("+c.owner+")包的Scope中删除");
//            c.name = name;
//            c.owner = owner;
//            c.fullname = ClassSymbol.formFullName(name, owner);
//            DEBUG.P("c.fullname(注意分析)="+c.fullname);
//            
//        }
//        //DEBUG.P("c.owner="+c.owner);
//        DEBUG.P(0,this,"enterClass(Name name, TypeSymbol owner)");
//        return c;
//    }
//
//    /**
//     * Creates a new toplevel class symbol with given flat name and
//     * given class (or source) file.
//     *
//     * @param flatName a fully qualified binary class name
//     * @param classFile the class file or compilation unit defining
//     * the class (may be {@code null})
//     * @return a newly created class symbol
//     * @throws AssertionError if the class symbol already exists
//     */
//    public ClassSymbol enterClass(Name flatName, JavaFileObject classFile) {
//    	DEBUG.P(this,"enterClass(2)");
//    	DEBUG.P("flatName="+flatName+" classFile="+classFile);
//        ClassSymbol cs = classes.get(flatName);
//        if (cs != null) {
//            String msg = Log.format("%s: completer = %s; class file = %s; source file = %s",
//                                    cs.fullname,
//                                    cs.completer,
//                                    cs.classfile,
//                                    cs.sourcefile);
//            throw new AssertionError(msg);
//        }
//        Name packageName = Convert.packagePart(flatName);
//        DEBUG.P("packageName="+packageName);
//        /*
//        syms未检测是否为null,会出现小问题(参见Symtab类中的注释)
//        syms是在protected ClassReader(Context context, boolean definitive)中通过
//        "syms = Symtab.instance(context);"进行初始化的，但在执行Symtab.instance(context)的过
//        程中又会在Symtab(Context context)中间接执行到这里，但此时并没有完成
//        Symtab(Context context)，也就是syms没有初始化，当执行syms.unnamedPackage时就会引起
//        java.lang.NullPointerException
//        */
//        PackageSymbol owner = packageName.isEmpty()
//				? syms.unnamedPackage
//				: enterPackage(packageName);
//        cs = defineClass(Convert.shortName(flatName), owner);
//        cs.classfile = classFile;
//        classes.put(flatName, cs);
//
//        DEBUG.P(0,this,"enterClass(2)");
//        return cs;
//    }
//
//    /** Create a new member or toplevel class symbol with given flat name
//     *  and enter in `classes' unless already there.
//     */
//    public ClassSymbol enterClass(Name flatname) {
//		try {//我加上的
//		DEBUG.P(this,"enterClass(1)");
//		
//        ClassSymbol c = classes.get(flatname);
//        if(c!=null) DEBUG.P("ClassSymbol("+flatname+")已存在");
//        //DEBUG.P("ClassSymbol c="+(JavaFileObject)null);//呵呵，第一次见这种语法(JavaFileObject)null
//        /*2008-11-15更正:
//		因为上面有两个方法:
//		1.public ClassSymbol enterClass(Name name, TypeSymbol owner)
//		2.public ClassSymbol enterClass(Name flatName, JavaFileObject classFile)
//		如果用这种方式调用:enterClass(flatname, null)
//		将产生编译错误:对enterClass的引用不明确
//		因为null既可以赋给TypeSymbol owner也可赋给JavaFileObject classFile
//		所以必须用类型转换:(JavaFileObject)null，告诉编译器它调用的是方法2
//		*/
//		if (c == null)
//            return enterClass(flatname, (JavaFileObject)null);
//        else
//            return c;
//            
//        }finally{//我加上的
//		DEBUG.P(1,this,"enterClass(1)");
//		}
//    }
//
//    private boolean suppressFlush = false;
//
//    /** Completion for classes to be loaded. Before a class is loaded
//     *  we make sure its enclosing class (if any) is loaded.
//     */
//    //complete(Symbol sym)这个方法最终的主要功能就是
//    //对ClassSymbol或PackageSymbol的members_field赋值
//    public void complete(Symbol sym) throws CompletionFailure {
//    	DEBUG.P(this,"complete(1)");
//    	DEBUG.P("SymbolKind="+Kinds.toString(sym.kind));
//        DEBUG.P("SymbolName="+sym);
//        DEBUG.P("filling="+filling+" suppressFlush="+suppressFlush);
//        //注:sym.kind的值是在com.sun.tools.javac.code.Kinds类中定义
//        if (sym.kind == TYP) {
//            ClassSymbol c = (ClassSymbol)sym;
//            c.members_field = new Scope.ErrorScope(c); // make sure it's always defined
//            boolean suppressFlush = this.suppressFlush;
//            this.suppressFlush = true;
//            try {
//				DEBUG.P("c.owner="+c.owner);
//                completeOwners(c.owner);
//                completeEnclosing(c);
//            } finally {
//                this.suppressFlush = suppressFlush;
//            }
//            fillIn(c);
//        } else if (sym.kind == PCK) {
//            PackageSymbol p = (PackageSymbol)sym;
//            try {
//                fillIn(p);
//            } catch (IOException ex) {
//                throw new CompletionFailure(sym, ex.getLocalizedMessage()).initCause(ex);
//            }
//        }
//        
//        DEBUG.P("filling="+filling+" suppressFlush="+suppressFlush);
//        if (!filling && !suppressFlush)
//            annotate.flush(); // finish attaching annotations
//       	DEBUG.P(2,this,"complete(1)");
//    }
//
//    /** complete up through the enclosing package. */
//    private void completeOwners(Symbol o) {
//		DEBUG.P(this,"completeOwners(1)");
//		DEBUG.P("o.kind="+Kinds.toString(o.kind));
//		
//        if (o.kind != PCK) completeOwners(o.owner);
//        o.complete();
//		DEBUG.P(0,this,"completeOwners(1)");
//    }
//
//    /**
//     * Tries to complete lexically enclosing classes if c looks like a
//     * nested class.  This is similar to completeOwners but handles
//     * the situation when a nested class is accessed directly as it is
//     * possible with the Tree API or javax.lang.model.*.
//     */
//    private void completeEnclosing(ClassSymbol c) {
//        DEBUG.P(this,"completeEnclosing(1)");
//    	DEBUG.P("c.owner.kind="+Kinds.toString(c.owner.kind));
//        
//        //如果有类名:my.test.ClassA$ClassB$ClassC
//        //则分解为my.test.ClassA my.test.ClassB my.test.ClassC
//        if (c.owner.kind == PCK) {
//            Symbol owner = c.owner;
//            DEBUG.P("c.owner="+c.owner);
//            DEBUG.P("c.name="+c.name);
//            DEBUG.P("Convert.shortName(c.name)="+Convert.shortName(c.name));
//            DEBUG.P("Convert.enclosingCandidates(Convert.shortName(c.name)="+Convert.enclosingCandidates(Convert.shortName(c.name)));
//            for (Name name : Convert.enclosingCandidates(Convert.shortName(c.name))) {
//                Symbol encl = owner.members().lookup(name).sym;
//                DEBUG.P("encl="+encl);
//                if (encl == null)
//                    encl = classes.get(TypeSymbol.formFlatName(name, owner));
//                DEBUG.P("encl="+encl);
//                if (encl != null)
//                    encl.complete();
//            }
//        }
//    	DEBUG.P(0,this,"completeEnclosing(1)");
//    }
//
//    /** We can only read a single class file at a time; this
//     *  flag keeps track of when we are currently reading a class
//     *  file.
//     */
//    private boolean filling = false;
//
//    /** Fill in definition of class `c' from corresponding class or
//     *  source file.
//     */
//    private void fillIn(ClassSymbol c) {
//    	try {//我加上的
//        DEBUG.P(this,"fillIn(ClassSymbol c)");
//        DEBUG.P("completionFailureName="+completionFailureName);
//        DEBUG.P("c.fullname="+c.fullname);
//	
//        //加-XDfailcomplete=-XDfailcomplete=java.lang.annotation
//        if (completionFailureName == c.fullname) {
//            throw new CompletionFailure(c, "user-selected completion failure by class name");
//        }
//        currentOwner = c;
//        JavaFileObject classfile = c.classfile;
//        DEBUG.P("classfile="+classfile);
//        if (classfile != null) {
//            // <editor-fold defaultstate="collapsed">
//            JavaFileObject previousClassFile = currentClassFile;
//            try {
//                assert !filling :
//                    "Filling " + classfile.toUri() +
//                    " during " + previousClassFile;
//                currentClassFile = classfile;
//                if (verbose) {
//                    printVerbose("loading", currentClassFile.toString());
//                }
//                if (classfile.getKind() == JavaFileObject.Kind.CLASS) {
//                    filling = true;
//                    try {
//                        bp = 0;
//                        buf = readInputStream(buf, classfile.openInputStream());
//                        DEBUG.P("");
//                        DEBUG.P("bp="+bp);
//                        DEBUG.P("buf.length="+buf.length);
//                        DEBUG.P("currentOwner="+currentOwner);
//                        DEBUG.P("currentOwner.type="+currentOwner.type);
//                        readClassFile(c);
//                        
//                        DEBUG.P("missingTypeVariables="+missingTypeVariables);
//                        DEBUG.P("foundTypeVariables  ="+foundTypeVariables);
//                        if (!missingTypeVariables.isEmpty() && !foundTypeVariables.isEmpty()) {
//                            List<Type> missing = missingTypeVariables;
//                            List<Type> found = foundTypeVariables;
//                            missingTypeVariables = List.nil();
//                            foundTypeVariables = List.nil();
//                            filling = false;
//                            ClassType ct = (ClassType)currentOwner.type;
//                            ct.supertype_field =
//                                types.subst(ct.supertype_field, missing, found);
//                            ct.interfaces_field =
//                                types.subst(ct.interfaces_field, missing, found);
//                        } else if (missingTypeVariables.isEmpty() !=
//                                   foundTypeVariables.isEmpty()) {
//                            /*注意:
//                            false!=false => false
//                            false!=true  => true
//                            true!=false  => true
//                            true!=true   => false
//                            */
//                            Name name = missingTypeVariables.head.tsym.name;
//                            throw badClassFile("undecl.type.var", name);
//                        }
//                    } finally {
//                        missingTypeVariables = List.nil();
//                        foundTypeVariables = List.nil();
//                        filling = false;
//                    }
//                } else {
//                    //如果找到的是(.java)源文件则调用JavaCompiler.complete(1)方法从源码编译
//                    if (sourceCompleter != null) {
//                        sourceCompleter.complete(c);
//                    } else {
//                        throw new IllegalStateException("Source completer required to read "
//                                                        + classfile.toUri());
//                    }
//                }
//                return;
//            } catch (IOException ex) {
//                throw badClassFile("unable.to.access.file", ex.getMessage());
//            } finally {
//                currentClassFile = previousClassFile;
//            }
//            // </editor-fold>
//        } else {
//            throw
//                newCompletionFailure(c,
//                                     Log.getLocalizedString("class.file.not.found",
//                                                            c.flatname));
//        }
//        
//        }finally{//我加上的
//        DEBUG.P(0,this,"fillIn(ClassSymbol c)");
//        }
//		
//    }
//    // where
//        private static byte[] readInputStream(byte[] buf, InputStream s) throws IOException {
//            try {
//                buf = ensureCapacity(buf, s.available());
//                int r = s.read(buf);
//                int bp = 0;
//                while (r != -1) {
//                    bp += r;
//                    buf = ensureCapacity(buf, bp);
//                    r = s.read(buf, bp, buf.length - bp);
//                }
//                return buf;
//            } finally {
//                try {
//                    s.close();
//                } catch (IOException e) {
//                    /* Ignore any errors, as this stream may have already
//                     * thrown a related exception which is the one that
//                     * should be reported.
//                     */
//                }
//            }
//        }
//        private static byte[] ensureCapacity(byte[] buf, int needed) {
//            if (buf.length < needed) {
//                byte[] old = buf;
//                buf = new byte[Integer.highestOneBit(needed) << 1];
//                System.arraycopy(old, 0, buf, 0, old.length);
//            }
//            return buf;
//        }
//        /** Static factory for CompletionFailure objects.
//         *  In practice, only one can be used at a time, so we share one
//         *  to reduce the expense of allocating new exception objects.
//         */
//        private CompletionFailure newCompletionFailure(ClassSymbol c,
//                                                       String localized) {
//            try {//我加上的
//            DEBUG.P(this,"newCompletionFailure(1)");
//            DEBUG.P("c="+c);
//            DEBUG.P("localized="+localized);
//            DEBUG.P("cacheCompletionFailure="+cacheCompletionFailure);
//            
//            if (!cacheCompletionFailure) {
//                // log.warning("proc.messager",
//                //             Log.getLocalizedString("class.file.not.found", c.flatname));
//                // c.debug.printStackTrace();
//                return new CompletionFailure(c, localized);
//            } else {
//                CompletionFailure result = cachedCompletionFailure;
//                result.sym = c;
//                result.errmsg = localized;
//                return result;
//            }
//            
//            }finally{//我加上的
//            DEBUG.P(0,this,"newCompletionFailure(1)");
//            }
//        }
//        private CompletionFailure cachedCompletionFailure =
//            new CompletionFailure(null, null);
//        //注意这里的一对｛｝中的内容在编译期间会收集到ClassReader的构造方法中
//        {
//            cachedCompletionFailure.setStackTrace(new StackTraceElement[0]);
//        }
//
//    /** Load a toplevel class with given fully qualified name
//     *  The class is entered into `classes' only if load was successful.
//     */
//    public ClassSymbol loadClass(Name flatname) throws CompletionFailure {
//        try {
//        //DEBUG.on();
//        DEBUG.P(this,"loadClass(Name flatname)");
//        DEBUG.P("flatname="+flatname);
//			
//
//        boolean absent = classes.get(flatname) == null;
//        DEBUG.P("absent="+absent);
//        ClassSymbol c = enterClass(flatname);
//        if (c.members_field == null && c.completer != null) {
//            try {
//                c.complete();
//            } catch (CompletionFailure ex) {
//				DEBUG.P("absent="+absent);
//				DEBUG.P("ex="+ex);
//                if (absent) classes.remove(flatname);
//                throw ex;
//            }
//        }
//        
//        return c;
//        
//        }finally{
//        DEBUG.P(0,this,"loadClass(Name flatname)");
//        //DEBUG.off();
//        }
//    }
//
///************************************************************************
// * Loading Packages
// ***********************************************************************/
//
//    /** Check to see if a package exists, given its fully qualified name.
//     */
//    public boolean packageExists(Name fullname) {
//    	try {//我加上的
//		DEBUG.P(this,"packageExists(Name fullname)");
//		DEBUG.P("fullname="+fullname);
//
//        return enterPackage(fullname).exists();
//        
//        }finally{//我加上的
//		DEBUG.P(0,this,"packageExists(Name fullname)");
//		}
//    }
//
//    /** Make a package, given its fully qualified name.
//     */
//// <editor-fold defaultstate="collapsed">
//	/*
//	当packageName=java.lang,首次调用enterPackage()时的输出:
//	com.sun.tools.javac.jvm.ClassReader===>enterPackage(1)
//	-------------------------------------------------------------------------
//	fullname=java.lang
//	Convert.shortName(fullname)=lang
//	Convert.packagePart(fullname)=java
//	com.sun.tools.javac.jvm.ClassReader===>enterPackage(1)
//	-------------------------------------------------------------------------
//	fullname=java
//	Convert.shortName(fullname)=java
//	Convert.packagePart(fullname)=
//	com.sun.tools.javac.jvm.ClassReader===>enterPackage(1)
//	-------------------------------------------------------------------------
//	fullname=
//	com.sun.tools.javac.jvm.ClassReader===>enterPackage(1)  END
//	-------------------------------------------------------------------------
//	com.sun.tools.javac.jvm.ClassReader===>enterPackage(1)  END
//	-------------------------------------------------------------------------
//	com.sun.tools.javac.jvm.ClassReader===>enterPackage(1)  END
//	-------------------------------------------------------------------------
//	*/
//// </editor-fold>
//    public PackageSymbol enterPackage(Name fullname) {
//    	DEBUG.P(this,"enterPackage(1)");
//        DEBUG.P("fullname="+fullname);
//		
//        //packages是一个Map
//        PackageSymbol p = packages.get(fullname);
//        if (p == null) {
//            //断言:当assert后面的条件为真时执行assert语句后的其他语句，否则报错退出。
//            //p == null且fullname也是一个空串(fullname=names.empty)这两个条件不会同时发生，
//            //因为空串(fullname=names.empty)在初始化Systab类时已跟PackageSymbol rootPackage对应
//            //且PackageSymbol rootPackage已放入packages
//            assert !fullname.isEmpty() : "rootPackage missing!";
//            
//            DEBUG.P("Convert.shortName(fullname)="+Convert.shortName(fullname));
//            DEBUG.P("Convert.packagePart(fullname)="+Convert.packagePart(fullname));
//            
//            /*
//            如果fullname从没出现过，一般会递归调用到当fullname是names.empty(Table.empty)时结束,
//            rootPackage的fullname就是names.empty,在init()时已加进packages.
//            另外,PackageSymbol类是按包名的逆序递归嵌套的,内部字段Symbol owner就是下面代码中
//            的enterPackage(Convert.packagePart(fullname))
//
//            举例:包名my.test的嵌套格式如下:
//            PackageSymbol {
//                    Name name="test";
//                    Symbol owner=new PackageSymbol {
//                            Name name="my";
//                            Symbol owner=rootPackage = new PackageSymbol(names.empty, null);
//                    }
//            }
//            */
//            p = new PackageSymbol(
//                Convert.shortName(fullname),
//                enterPackage(Convert.packagePart(fullname)));
//            //这一步是为了以后调用Symbol.complete()来间接调用ClassReader的complete(Symbol sym)
//            p.completer = this;
//            packages.put(fullname, p);
//        }
//        DEBUG.P(0,this,"enterPackage(1)");
//        return p;
//    }
//
//    /** Make a package, given its unqualified name and enclosing package.
//     */
//    public PackageSymbol enterPackage(Name name, PackageSymbol owner) {
//        return enterPackage(TypeSymbol.formFullName(name, owner));
//    }
//
//    /** Include class corresponding to given class file in package,
//     *  unless (1) we already have one the same kind (.class or .java), or
//     *         (2) we have one of the other kind, and the given class file
//     *             is older.
//     */
//    protected void includeClassFile(PackageSymbol p, JavaFileObject file) {
//    	DEBUG.P("");
//    	DEBUG.P(this,"includeClassFile(2)");
//    	DEBUG.P("PackageSymbol p.flags_field="+p.flags_field+" ("+Flags.toString(p.flags_field)+")");
//    	DEBUG.P("p.members_field="+p.members_field);
//    	
//    	//检查PackageSymbol是否已有成员(以前有没有ClassSymbol加进了members_field)
//    	//另外只要子包已有成员，那么就认为子包的所有owner都已有成员
//    	//另请参考Flags类的EXISTS字段说明
//        if ((p.flags_field & EXISTS) == 0)
//            for (Symbol q = p; q != null && q.kind == PCK; q = q.owner)
//                q.flags_field |= EXISTS;
//        JavaFileObject.Kind kind = file.getKind();
//        int seen;
//        if (kind == JavaFileObject.Kind.CLASS)
//            seen = CLASS_SEEN;//CLASS_SEEN在Flags类中定义
//        else
//            seen = SOURCE_SEEN;
//        
//        //binaryName在先前的fillIn(3)中已找过一次了,这里又找了一次,
//        //可以适当改进一下,因为调用inferBinaryName方法还是耗时间的
//        String binaryName = fileManager.inferBinaryName(currentLoc, file);
//        DEBUG.P("binaryName="+binaryName);
//        int lastDot = binaryName.lastIndexOf(".");
//        Name classname = names.fromString(binaryName.substring(lastDot + 1));
//        DEBUG.P("classname="+classname);
//        boolean isPkgInfo = classname == names.package_info;
//        ClassSymbol c = isPkgInfo
//            ? p.package_info
//            : (ClassSymbol) p.members_field.lookup(classname).sym;
//        DEBUG.P("ClassSymbol c="+c);
//        if (c != null) DEBUG.P("在包("+p+")的Scope中已有这个ClassSymbol");
//        if (c == null) {
//            c = enterClass(classname, p);
//            if (c.classfile == null) // only update the file if's it's newly created
//                c.classfile = file;
//            if (isPkgInfo) {
//                p.package_info = c;
//            } else {
//            	DEBUG.P("c="+c+" c.owner="+c.owner+" p="+p);
//            	if(c.owner != p) 
//            		DEBUG.P("(内部类没有Enter到包Scope)");
//            	else 
//            		DEBUG.P("(已Enter到包Scope)");
//            	/*
//            	也就是说PackageSymbol的members_field不会含有内部类
//            	这是因为在enterClass(classname, p)的内部可以改变
//            	c的owner,而不一定是传进去的参数PackageSymbol p.
//            	
//            	但是还是奇怪,如下代码:
//            	package my.test;
//            	public class Test{
//					public class MyInnerClass {
//					}
//				}
//				打印结果还是:
//				c=my.test.Test$MyInnerClass c.owner=my.test p=my.test
//				*/
//                if (c.owner == p)  // it might be an inner class
//                    p.members_field.enter(c);
//            }
//        //在类路径中找到包名与类名相同的多个文件时，
//        //1.如果文件扩展名相同，则选先找到的那一个(也就是说不管后面出现的再如何新也不会选中)
//        //2.如果文件扩展名不同且在javac中加上“-Xprefer:source”选项时，则选源文件(.java)
//        //3.如果文件扩展名不同且在javac中没有加“-Xprefer:source”选项，则选最近修改过的那一个
//        
//        //(c.flags_field & seen) == 0)表示原先的ClassSymbol所代表的文件
//        //的扩展名与现在的file所代表的文件的扩展名不同
//        } else if (c.classfile != null && (c.flags_field & seen) == 0) {
//        	DEBUG.P("ClassSymbol c.classfile(旧)="+c.classfile);
//            // if c.classfile == null, we are currently compiling this class
//            // and no further action is necessary.
//            // if (c.flags_field & seen) != 0, we have already encountered
//            // a file of the same kind; again no further action is necessary.
//            if ((c.flags_field & (CLASS_SEEN | SOURCE_SEEN)) != 0)
//                c.classfile = preferredFileObject(file, c.classfile);
//        }
//        c.flags_field |= seen;
//        DEBUG.P("ClassSymbol c.classfile="+c.classfile);
//        DEBUG.P("ClassSymbol c.flags_field="+c.flags_field+" ("+Flags.toString(c.flags_field)+")");
//        DEBUG.P(1,this,"includeClassFile(2)");
//    }
//
//    /** Implement policy to choose to derive information from a source
//     *  file or a class file when both are present.  May be overridden
//     *  by subclasses.
//     */
//    protected JavaFileObject preferredFileObject(JavaFileObject a,
//                                           JavaFileObject b) {
//        
//        if (preferSource)
//            return (a.getKind() == JavaFileObject.Kind.SOURCE) ? a : b;
//        else {
//            long adate = a.getLastModified();
//            long bdate = b.getLastModified();
//            // 6449326: policy for bad lastModifiedTime in ClassReader
//            //assert adate >= 0 && bdate >= 0;
//            return (adate > bdate) ? a : b;
//        }
//    }
//
//    /**
//     * specifies types of files to be read when filling in a package symbol
//     */
//    protected EnumSet<JavaFileObject.Kind> getPackageFileKinds() {
//        return EnumSet.of(JavaFileObject.Kind.CLASS, JavaFileObject.Kind.SOURCE);
//    }
//
//    /**
//     * this is used to support javadoc
//     */
//    protected void extraFileActions(PackageSymbol pack, JavaFileObject fe) {
//    }
//
//    protected Location currentLoc; // FIXME
//
//    private boolean verbosePath = true;
//
//    /** Load directory of package into members scope.
//     */
//    private void fillIn(PackageSymbol p) throws IOException {
//    	DEBUG.P(this,"fillIn(PackageSymbol p)");
//    	DEBUG.P("Scope members_field="+p.members_field);
//        if (p.members_field == null) p.members_field = new Scope(p);
//        String packageName = p.fullname.toString();
//        
//        //这里的包名所代表的目录下面的文件可以是“.class”和“.java”
//        Set<JavaFileObject.Kind> kinds = getPackageFileKinds();
//        
//        //PLATFORM_CLASS_PATH在javax.tools.StandardLocation中定义
//        //DEBUG.P("fileManager.getClass().getName()="+fileManager.getClass().getName(),true);
//        //输出如:com.sun.tools.javac.util.JavacFileManager
//        
//        //这里是在PLATFORM_CLASS_PATH上搜索packageName目录下的所有class文件
//        fillIn(p, PLATFORM_CLASS_PATH,
//               fileManager.list(PLATFORM_CLASS_PATH,
//                                packageName,
//                                EnumSet.of(JavaFileObject.Kind.CLASS),
//                                false));
//        
//        DEBUG.P(2);
//        DEBUG.P("***从PLATFORM_CLASS_PATH中Enter类文件结果如下***");
//        DEBUG.P("-----------------------------------------------");
//        DEBUG.P("包名: "+packageName);
//        DEBUG.P("成员: "+p.members_field);
//       	DEBUG.P(2);
// 
//        DEBUG.P("kinds="+kinds);                       
//        Set<JavaFileObject.Kind> classKinds = EnumSet.copyOf(kinds);
//        DEBUG.P("classKinds1="+classKinds); 
//        classKinds.remove(JavaFileObject.Kind.SOURCE);
//        DEBUG.P("classKinds2="+classKinds);
//        boolean wantClassFiles = !classKinds.isEmpty();
//
//        Set<JavaFileObject.Kind> sourceKinds = EnumSet.copyOf(kinds);
//        sourceKinds.remove(JavaFileObject.Kind.CLASS);
//        boolean wantSourceFiles = !sourceKinds.isEmpty();
//
//        boolean haveSourcePath = fileManager.hasLocation(SOURCE_PATH);
//        
//        DEBUG.P("sourceKinds="+sourceKinds);
//        DEBUG.P("wantClassFiles="+wantClassFiles);
//        DEBUG.P("wantSourceFiles="+wantSourceFiles);
//        DEBUG.P("haveSourcePath="+haveSourcePath);
//        DEBUG.P("verbose="+verbose);
//        DEBUG.P("verbosePath="+verbosePath);
//
//        if (verbose && verbosePath) {
//        	//javac加-verbose时输出[search path for source files:.....]
//        	//[search path for class files:...........................]
//            if (fileManager instanceof StandardJavaFileManager) {
//                StandardJavaFileManager fm = (StandardJavaFileManager)fileManager;
//                //加了-sourcepath选项时，打印-sourcepath所指示的路径
//                //路径由com.sun.tools.javac.util.Paths.computeSourcePath()求出
//                if (haveSourcePath && wantSourceFiles) {
//                    List<File> path = List.nil();
//                    for (File file : fm.getLocation(SOURCE_PATH)) {
//                    	DEBUG.P("file="+file);
//                        path = path.prepend(file);
//                    }
//                    printVerbose("sourcepath", path.reverse().toString());
//                //没加-sourcepath选项时,默认打印类路径上的信息
//                //路径由com.sun.tools.javac.util.Paths.computeUserClassPath()求出
//                } else if (wantSourceFiles) {
//                    List<File> path = List.nil();
//                    for (File file : fm.getLocation(CLASS_PATH)) {
//                        path = path.prepend(file);
//                    }
//                    printVerbose("sourcepath", path.reverse().toString());
//                }
//                if (wantClassFiles) {
//                    List<File> path = List.nil();
//                    //一般是jre\lib和jre\lib\ext目录下的.jar文件
//                    //路径由com.sun.tools.javac.util.Paths.computeBootClassPath()求出
//                    for (File file : fm.getLocation(PLATFORM_CLASS_PATH)) {
//                        path = path.prepend(file);
//                    }
//                    
//                    //路径由com.sun.tools.javac.util.Paths.computeUserClassPath()求出
//                    for (File file : fm.getLocation(CLASS_PATH)) {
//                        path = path.prepend(file);
//                    }
//                    //将上面两种类路径连在一起输出
//                    printVerbose("classpath",  path.reverse().toString());
//                }
//            }
//        }
//        
//        //当没指定-sourcepath时，默认在CLASS_PATH上搜索packageName目录下的所有class及java文件
//        if (wantSourceFiles && !haveSourcePath) {
//            fillIn(p, CLASS_PATH,
//                   fileManager.list(CLASS_PATH,
//                                    packageName,
//                                    kinds,
//                                    false));
//        } else {
//        	//在CLASS_PATH上搜索packageName目录下的所有class文件
//            if (wantClassFiles)
//                fillIn(p, CLASS_PATH,
//                       fileManager.list(CLASS_PATH,
//                                        packageName,
//                                        classKinds,
//                                        false));
//            //在SOURCE_PATH上搜索packageName目录下的所有java文件
//            if (wantSourceFiles)
//                fillIn(p, SOURCE_PATH,
//                       fileManager.list(SOURCE_PATH,
//                                        packageName,
//                                        sourceKinds,
//                                        false));
//        }
//        verbosePath = false;
//        
//        //成员也有可能是未编译的.java文件
//        DEBUG.P(2);
//        DEBUG.P("***所有成员Enter结果如下***");
//        DEBUG.P("-----------------------------------------------");
//        DEBUG.P("包名: "+packageName);
//        DEBUG.P("成员: "+p.members_field);
//        DEBUG.P(2,this,"fillIn(PackageSymbol p)"); 
//    }
//    // where
//        private void fillIn(PackageSymbol p,
//                            Location location,
//                            Iterable<JavaFileObject> files)
//        {
//            currentLoc = location;
//            DEBUG.P(this,"fillIn(3)");
//           
//            for (JavaFileObject fo : files) {
//            	DEBUG.P("fileKind="+fo.getKind()+" fileName="+fo);
//                switch (fo.getKind()) {
//                case CLASS:
//                case SOURCE: {
//                    // TODO pass binaryName to includeClassFile
//                    String binaryName = fileManager.inferBinaryName(currentLoc, fo);
//                    String simpleName = binaryName.substring(binaryName.lastIndexOf(".") + 1);
//                    DEBUG.P("binaryName="+binaryName+" simpleName="+simpleName);
//                    if (SourceVersion.isIdentifier(simpleName) ||
//                        simpleName.equals("package-info"))
//                        includeClassFile(p, fo);
//                    break;
//                }
//                default:
//                    extraFileActions(p, fo);//一个空方法
//                }
//                DEBUG.P(1);
//            }
//            DEBUG.P(2,this,"fillIn(3)");
//        }
//
//    /** Output for "-verbose" option.
//     *  @param key The key to look up the correct internationalized string.
//     *  @param arg An argument for substitution into the output string.
//     */
//    private void printVerbose(String key, CharSequence arg) {
//        Log.printLines(log.noticeWriter, Log.getLocalizedString("verbose." + key, arg));
//    }
//
//    /** Output for "-checkclassfile" option.
//     *  @param key The key to look up the correct internationalized string.
//     *  @param arg An argument for substitution into the output string.
//     */
//    private void printCCF(String key, Object arg) {
//        Log.printLines(log.noticeWriter, Log.getLocalizedString(key, arg));
//    }
//
//
//    public interface SourceCompleter {
//        void complete(ClassSymbol sym)
//            throws CompletionFailure;
//    }
//
//    /**
//     * A subclass of JavaFileObject for the sourcefile attribute found in a classfile.
//     * The attribute is only the last component of the original filename, so is unlikely
//     * to be valid as is, so operations other than those to access the name throw
//     * UnsupportedOperationException
//     */
//    private static class SourceFileObject extends BaseFileObject {
//
//        /** The file's name.
//         */
//        private Name name;
//
//        public SourceFileObject(Name name) {
//            this.name = name;
//        }
//
//        public InputStream openInputStream() {
//            throw new UnsupportedOperationException();
//        }
//
//        public OutputStream openOutputStream() {
//            throw new UnsupportedOperationException();
//        }
//
//        public Reader openReader() {
//            throw new UnsupportedOperationException();
//        }
//
//        public Writer openWriter() {
//            throw new UnsupportedOperationException();
//        }
//
//        /** @deprecated see bug 6410637 */
//        @Deprecated
//        public String getName() {
//            return name.toString();
//        }
//
//        public long getLastModified() {
//            throw new UnsupportedOperationException();
//        }
//
//        public boolean delete() {
//            throw new UnsupportedOperationException();
//        }
//
//        public CharBuffer getCharContent(boolean ignoreEncodingErrors) {
//            throw new UnsupportedOperationException();
//        }
//
//        @Override
//        public boolean equals(Object other) {
//            if (!(other instanceof SourceFileObject))
//                return false;
//            SourceFileObject o = (SourceFileObject) other;
//            return name.equals(o.name);
//        }
//
//        @Override
//        public int hashCode() {
//            return name.hashCode();
//        }
//
//        public boolean isNameCompatible(String simpleName, JavaFileObject.Kind kind) {
//            return true; // fail-safe mode
//        }
//
//        public URI toUri() {
//            return URI.create(name.toString());
//        }
//
//        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
//            throw new UnsupportedOperationException();
//        }
//
//    }
//}
