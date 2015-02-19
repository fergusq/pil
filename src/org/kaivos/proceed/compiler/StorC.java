package org.kaivos.proceed.compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kaivos.proceed.compiler.Stor.Assembler;
import org.kaivos.proceed.parser.ProceedTree.FunctionTree;

class StorC extends Stor {
	StorC(ProceedCompiler proceedCompiler) {
		super(proceedCompiler);
		ASSEMBLER = Assembler.C;
	}

	@Override
	public String getVar(String name) {
		return getVarAddr(name);
	}
	
	@Override
	public String getVarAddr(String name) {
		if (proceedCompiler.statics.contains(name)) {
			return "" + proceedCompiler.censor(name) + "";
		}
		if (vars.contains(name)) {
			return "var" + vars.indexOf(name);
		}
		if (args != null && args.contains(name)) {
			return "arg" + args.indexOf(name);
		}
		if (name.startsWith("@")) {
			return "*(long*)(" + changeReg(name.substring(1)) + ")";
		}
		if (name.startsWith("arg$")) {
			//return "[" + Registers.BP + "+" + changeReg(name.substring(4)) + "*"+Registers.REGISTER_SIZE+"+"+Registers.REGISTER_SIZE*2+"]";
			return "0";
		}
		return changeReg(name);
	}
	
	@Override
	public String getVal(String name) {
		if (name.startsWith(":")) return getValAddr(name.substring(1));
		return getValAddr(name);
	}
	
	@Override
	public String getValAddr(String name) {
		if (proceedCompiler.constantValues.containsKey(name)) {
			return getValAddr(proceedCompiler.constantValues.get(name));
		}
		if (proceedCompiler.constantValues_r.containsKey(name)) {
			return getValAddr(proceedCompiler.constantValues_r.get(name));
		}
		if (proceedCompiler.statics.contains(name)) {
			return "" + proceedCompiler.censor(name) + "";
		}
		if (vars.contains(name)) {
			return "var" + vars.indexOf(name);
		}
		if (args != null && args.contains(name)) {
			return "arg" + args.indexOf(name);
		}
		if (name.startsWith("@")) {
			return "*(long*)(" + changeReg(name.substring(1)) + ")";
		}
		if (name.startsWith("arg$")) {
			//return "[" + Registers.BP + "+" + changeReg(name.substring(4)) + "*"+Registers.REGISTER_SIZE+"+"+Registers.REGISTER_SIZE*2+"]";
			return "0";
		}
		return changeReg(name);
	}
	
	@Override
	public String getHalfVarAddr(String name) {
		if (proceedCompiler.constantValues_r.containsKey(name)) {
			return getHalfVarAddr(proceedCompiler.constantValues_r.get(name));
		}
		if (proceedCompiler.statics.contains(name)) {
			return "&" + proceedCompiler.censor(name) + "";
		}
		if (vars.contains(name)) {
			return "&var" + vars.indexOf(name);
		}
		if (args != null && args.contains(name)) {
			return "&arg" + args.indexOf(name);
		}
		if (name.startsWith("@")) {
			return "(" + changeReg(name.substring(1)) + ")";
		}
		if (name.startsWith("arg$")) {
			//return "[" + Registers.BP + "+" + changeReg(name.substring(4)) + "*"+Registers.REGISTER_SIZE+"+"+Registers.REGISTER_SIZE*2+"]";
			return "0";
		}
		return changeReg(name);
	}
	
	private String changeReg(String substring) {
		if (substring.equals(Registers.AX)) substring = "oax";
		else if (substring.equals(Registers.BX)) substring = "obx";
		else if (substring.equals(Registers.CX)) substring = "ocx";
		else if (substring.equals(Registers.DX)) substring = "odx";
		else if (substring.equals(Registers.BP)) substring = "obp";
		else if (substring.equals(Registers.SP)) substring = "osp";
		else if (substring.equals(Registers.DI)) substring = "odi";
		else if (substring.equals(Registers.SI)) substring = "osi";
		switch (substring) {
		case "oax":
			return "RegAX";
		case "obx":
			return "RegBX";
		case "ocx":
			return "RegCX";
		case "odx":
			return "RegDX";
		case "obp":
			return "RegBP";
		case "osp":
			return "RegSP";
		case "osi":
			return "RegSI";
		case "odi":
			return "RegDI";
			
		case "osize":
			return "sizeof(void*)";

		default:
			break;
		}
		try {
			Integer.parseInt(substring);
			return substring;
		} catch (NumberFormatException e) {
			return proceedCompiler.censor(substring);
		}
	}

	// Instructions
	
	@Override
	public void move(String s, String s1) {
		if (getVar(s).equals(getVal(s1))) return;
		out.println("\t" + getVar(s) + " = " + getVal(s1) + ";");
	}
	
	@Override
	public void mins(String ins, String s, String s1) {
		switch (ins) {
		case "add":
			ins = "+";
			break;
		case "sub":
			ins = "-";
			break;
		case "imul":
			ins = "*";
			break;
		case "_div_":
			ins = "/";
			break;
		case "_mod_":
			ins = "%";
			break;
		case "xor":
			ins = "^";
			break;
		case "and":
			ins = "&";
			break;
		case "or":
			ins = "|";
			break;
		case "_eq_":
			ins = "==";
			break;
		case "_neq_":
			ins = "!=";
			break;
		case "_lt_":
			ins = "<";
			break;
		case "_gt_":
			ins = ">";
			break;
		case "_le_":
			ins = "<=";
			break;
		case "_ge_":
			ins = ">=";
			break;
		default:
			break;
		}
		out.println("\t" + getVar(s) + " = (void*)(((long) " + getVar(s) + ") " + ins + " ((long) " + getVal(s1) + "));");
	}
	
	@Override
	public void lea(String s, String s1) {
		out.println("\t" + getVar(s) + " = " + getHalfVarAddr(s1) + ";");
	}
	
	@Override
	public void push(String s) {
		out.println("\tpush\t" + getVal(s));
	}
	
	private String cmp1;
	private String cmp2;
	
	@Override
	public void ins(String ins, String ...arguments) {
		if (ins.equals("cmp")) {
			cmp1 = getVal(arguments[0]);
			cmp2 = getVal(arguments[1]);
			return;
		}
		
		String a = "";
		for (int i = 0; i < arguments.length; i++) {
			if (i != 0) a += ", ";
			else a += "\t";
			a += getVal(arguments[i]);
		}
		out.println("\t" + ins + a);
	}
	
	@Override
	public void call(String to) {
		Collections.reverse(newArgs);
		out.print("\tRegAX = " + getVal(to) + "(");
		for (int i = 0; i < newArgs.size(); i++) {
			if (i != 0) out.print(", ");
			out.print(newArgs.get(i));
		}
		out.println(");");
	}
	
	@Override
	public void callPtr(String to) {
		Collections.reverse(newArgs);
		String type = "void*(*)(";
		for (int i = 0; i < newArgs.size(); i++) {
			if (i != 0) type += (", ");
			type += "void*";
		}
		type += ")";
		out.print("\tRegAX = ((" + type + ")" + getVal(to) + ")(");
		for (int i = 0; i < newArgs.size(); i++) {
			if (i != 0) out.print(", ");
			out.print(newArgs.get(i));
		}
		out.println(");");
	}
	
	@Override
	public void callm(String to, String name) {
		Collections.reverse(newArgs);
		out.print("\t" + getVar(to) + " = " + getVal(name) + "(");
		for (int i = 0; i < newArgs.size(); i++) {
			if (i != 0) out.print(", ");
			out.print(newArgs.get(i));
		}
		out.println(");");
	}
	
	@Override
	public void callPtrm(String to, String name) {
		Collections.reverse(newArgs);
		String type = "void*(*)(";
		for (int i = 0; i < newArgs.size(); i++) {
			if (i != 0) type += (", ");
			type += "void*";
		}
		type += ")";
		out.print("\t" + getVar(to) + " = ((" + type + ")" + getVal(name) + ")(");
		for (int i = 0; i < newArgs.size(); i++) {
			if (i != 0) out.print(", ");
			out.print(newArgs.get(i));
		}
		out.println(");");
	}
	
	@Override
	public void label(String name) {
		out.println("\tL" + proceedCompiler.censor(name) + ":");
	}
	
	@Override
	public void jump(String to) {
		out.println("\tgoto\tL" + proceedCompiler.censor(to) + ";");
	}
	
	@Override
	public void jump(String cond, String to) {
		switch (cond) {
		case "l":
			cond = "<";
			break;
		case "g":
			cond = ">";
			break;
		case "le":
			cond = "<=";
			break;
		case "ge":
			cond = ">=";
			break;
		case "e":
			cond = "==";
			break;
		case "ne":
			cond = "!=";
			break;

		default:
			break;
		}
		
		out.println("\tif ("+cmp1+" " + cond + " "+cmp2+") goto L" + proceedCompiler.censor(to) + ";");
	}
	
	@Override
	public void pset(String to, String from) {
		out.println("\t*(" + getVal(to) + ") = " + getVal(from) + ";");
	};
	
	// Functions
	
	List<String> newArgs = new ArrayList<>();
	
	@Override
	public void pushArg(int index, String arg) {
		newArgs.add(getVal(arg));
	}
	
	@Override
	public void clearArgs(int size) {
		newArgs.clear();
	}
	
	@Override
	public void function(String name, boolean varargs) {
		out.print("void * " + proceedCompiler.censor(name) + "(");
		for (int i = 0; i < args.size(); i++) {
			if (i != 0) out.print(", ");
			
			String type = "void*";
			if (types.contains(vartypes.get(args.get(i)))) {
				type = vartypes.get(args.get(i)).replace("_", "______").replace("<", "_").replace(">", "__").replace(", ", "___").replace(":", "____");
			}
			
			out.print(type + " arg" + i);
		}
		if (varargs) {
			if (args.size() != 0) out.print(", ");
			out.print("...");
		}
		out.println(") {");
	}
	
	@Override
	public void ret() {
		out.println("\treturn RegAX;");
		out.println("}");
	}
	
	@Override
	public void initStack() {
		stackInited = true;
		
		for (int i = 0; i < vars.size(); i++) {
			
			String type = "void*";
			if (types.contains(vartypes.get(vars.get(i)))) {
				type = vartypes.get(vars.get(i)).replace("_", "______").replace("<", "_").replace(">", "__").replace(", ", "___").replace(":", "____");
			}
			
			out.println("\t"+type+" var" + i + ";");
		}
		return;
	}
	@Override
	public void resetStack() {
		stackInited = false;

	}
	
	// Top-level static
	
	public static void start(File file, ArrayList<String> types, ArrayList<String[]> complexTypes) {
		out = ProceedCompiler.out;
		Stor.types = types;
		
		out.println();
		
		out.println("void * RegAX;");
		out.println("void * RegBX;");
		out.println("void * RegCX;");
		out.println("void * RegDX;");
		out.println("void * RegBP;");
		out.println("void * RegSP;");
		out.println("void * RegDI;");
		out.println("void * RegSI;");
		
		for (int i = 0; i < types.size(); i++) {
			String s = types.get(i);
			if (types.indexOf(types.get(i)) == i)
			{
				String ctype = "void*";
				
				out.println("typedef " + ctype + " " + censorPHLType(s) + ";");
			}
		}
	}
	
	private static String censorPHLType(String s) {
		return s
				.replace("_", "______")
				.replace("<", "_")
				.replace(">", "__")
				.replace(", ", "___")
				.replace(":", "____");
	}

	public static void global(ProceedCompiler comp, String name) {
		out.println("void * " + comp.censor(name) + ";");
	}
	
	public static void extern(ProceedCompiler comp, String name) {
		out.println("void * " + comp.censor(name) + "();");
	}
	
	public static void constant(ProceedCompiler comp, String name, List<String> data) {
		out.print("char "+comp.censor(name)+"[] = {");
		out.print(data.get(0));
		for (int i = 1; i < data.size(); i++) {
			out.print(", " + data.get(i));
		}
		out.println("};");
	}
	
	public static void staticdata(ProceedCompiler comp, String name) {
		out.println("void * " + comp.censor(name) + ";");
	}
	
	public static void func(ProceedCompiler comp, String name, FunctionTree f) {
		out.print("void * " + comp.censor(name) + "(");
		for (int i = 0; i < f.params.size(); i++) {
			if (i != 0) out.print(", ");
			out.print("void * arg" + i);
		}
		if (f.varargs) {
			if (f.params.size() != 0) out.print(", ");
			out.print("...");
		}
		out.println(");");
		
	}
	
	// Comments
	
	@Override
	public void comment(String text) {
		out.println("\t/* " + text.replace("/*", "/ *").replace("*/", "* /") + " */");
	}
	
	@Override
	public void space() {
		out.println();
	}
	
}