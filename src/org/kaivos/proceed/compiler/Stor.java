package org.kaivos.proceed.compiler;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.kaivos.proceed.parser.ProceedParser;

public class Stor {
	public enum Assembler {
		GAS,
		NASM,
		C
	}
	
	public static Assembler ASSEMBLER = Assembler.GAS;
	
	/**
	 * 
	 */
	protected final ProceedCompiler proceedCompiler;
	
	protected static PrintWriter out;

	/**
	 * @param proceedCompiler
	 */
	Stor(ProceedCompiler proceedCompiler) {
		this.proceedCompiler = proceedCompiler;
		out = ProceedCompiler.out;
	}

	List<String> vars;
	List<String> args;
	boolean argreg = false;
	boolean stackInited = false;

	public HashMap<String, String> vartypes;
	public static ArrayList<String> types;
	
	public static int FUNCTION_COUNTER = 1;
	
	public String getVar(String name) {
		if (ASSEMBLER == Assembler.GAS) {
			String s = getVarAddr(name);
			if (!s.matches("[er]([abcd]x|[sd]i|[89]|1[10])")) {
				if (s.startsWith("["))
					s = Registers.SIZE + " ptr " + s;
			}
			return s;
		}
		return Registers.SIZE + " " + getVarAddr(name);
	}
	
	public String getVarAddr(String name) {
		if (this.proceedCompiler.statics.contains(name)) {
			return "[" + ProceedCompiler.censor2(name) + "]";
		}
		if (vars.contains(name)) {
			return "[" + Registers.BP + "-" + (vars.indexOf(name)*Registers.REGISTER_SIZE+Registers.REGISTER_SIZE)+"]";
		}
		if (args != null && args.contains(name)) {
			if (Registers.ARCH.equals("amd64") ) {
				if (args.indexOf(name) < 6)
					return Registers.amd64Arguments[args.indexOf(name)];
				else return "[" + Registers.BP + "+" + ((args.indexOf(name)-6)*Registers.REGISTER_SIZE+Registers.REGISTER_SIZE*(args.size()>0?2:1))+"]";
			} else return "[" + Registers.BP + "+" + (args.indexOf(name)*Registers.REGISTER_SIZE+Registers.REGISTER_SIZE*(args.size()>0?2:1))+"]";
		}
		if (name.startsWith("@")) {
			return "[" + changeReg(name.substring(1)) + "]";
		}
		if (name.startsWith("arg$")) {
			return "[" + Registers.BP + "+" + changeReg(name.substring(4)) + "*"+Registers.REGISTER_SIZE+"+"+Registers.REGISTER_SIZE*(args.size()>0?2:1)+"]";
		}
		return changeReg(name);
	}
	
	public String getVal(String name) {
		if (name.startsWith(":")) return getValAddr(name.substring(1));
		if (ASSEMBLER == Assembler.GAS) {
			String s = getValAddr(name);
			if (!s.matches("[er]([abcd]x|[sd]i|[89]|1[10])")) {
				if (s.startsWith("[") || proceedCompiler.externs.contains(s))
					s = Registers.SIZE + " ptr " + s;
				else
					s = "offset flat:" + s;
			}
			return s;
		}
		return Registers.SIZE + " " + getValAddr(name);
	}
	
	public String getValAddr(String name) {
		if (this.proceedCompiler.constantValues.containsKey(name)) {
			return getValAddr(this.proceedCompiler.constantValues.get(name));
		}
		if (this.proceedCompiler.constantValues_r.containsKey(name)) {
			return getValAddr(this.proceedCompiler.constantValues_r.get(name));
		}
		return getVarAddr(name);
	}
	
	/**
	 * Palauttaa pelkän osoitteen lea-komentoa varten, voi olla myös alias
	 * @param name
	 * @return
	 */
	public String getHalfVarAddr(String name) {
		if (this.proceedCompiler.constantValues_r.containsKey(name)) {
			return getHalfVarAddr(this.proceedCompiler.constantValues_r.get(name));
		}
		return getVarAddr(name);
	}
	
	private static String changeReg(String substring) {
		switch (substring) {
		case "oax":
			return Registers.AX;
		case "obx":
			return Registers.BX;
		case "ocx":
			if (Registers.ARCH.equals("amd64")) return "r10";
			return Registers.CX;
		case "odx":
			if (Registers.ARCH.equals("amd64")) return "r11";
			return Registers.DX;
		case "obp":
			return Registers.BP;
		case "osp":
			return Registers.SP;
		case "osi":
			return Registers.SI;
		case "odi":
			return Registers.DI;
			
		case "osize":
			return ""+Registers.REGISTER_SIZE;

		default:
			break;
		}
		return ProceedCompiler.censor2(substring);
	}

	// Instructions
	
	public void move(String s, String s1) {
		if (getVar(s).equals(getVal(s1))) return;
		mins("mov", s, s1);
	}
	
	public void mins(String ins, String s, String s1) {
		out.println("\t"+ins+"\t" + getVar(s) + ", " + getVal(s1));
	}
	
	public void lea(String s, String s1) {
		out.println("\tlea\t" + getVar(s) + ", " + getHalfVarAddr(s1));
	}
	
	public void push(String s) {
		out.println("\tpush\t" + getVal(s));
	}
	
	public void ins(String ins, String ...arguments) {
		String a = "";
		for (int i = 0; i < arguments.length; i++) {
			if (i != 0) a += ", ";
			else a += "\t";
			a += getVal(arguments[i]);
		}
		out.println("\t" + ins + a);
	}
	
	public void call(String to) {
		out.println("\tcall\t" + ProceedCompiler.censor2(to));
	}
	
	public void callPtr(String to) {
		ins("call", to);
	}
	
	private static String localLabel(String name) {
		if (ASSEMBLER == Assembler.GAS) return ".L" + FUNCTION_COUNTER + "_" + name;
		else return ".L" + name;
	}
	
	public void label(String name) {
		out.println("\t" + localLabel(name) + ":");
	}
	
	public void jump(String to) {
		out.println("\tjmp\t" + localLabel(to));
	}
	
	public void jump(String cond, String to) {
		out.println("\tj" + cond + "\t" + localLabel(to));
	}
	
	public void pset(String to, String from) {
		move("oax", to);
		move(Registers.BX, from);
		move("@oax", Registers.BX);
	}
	
	// Functions
	
	public static String currentFunctionName = ":global:";
	
	public void pushArg(int index, String arg) {
		if (Registers.ARCH.equals("amd64") && index < 6) {
			out.println("\tmov\t" + Registers.amd64Arguments[index] + ", " + getVal(arg));
		} else push(arg);
	}
	
	public void clearArgs(int size) {
		if (size > 0)
			if (!Registers.ARCH.equals("amd64"))
				mins("add", Registers.SP, ""+(size*Registers.REGISTER_SIZE));
			else if (size >= 6)
				mins("add", Registers.SP, ""+((size-6)*Registers.REGISTER_SIZE));
	}
	
	public void function(String name, boolean varargs) {
		currentFunctionName = name;
		
		FUNCTION_COUNTER++;
		if (ASSEMBLER == Assembler.GAS) {
			STABS_function(name);
			out.println(".type " + ProceedCompiler.censor2(name) + ", @function");
		}
		out.println(ProceedCompiler.censor2(name) + ":");
	}
	
	public void ret() {
		ins("ret");
		if (ASSEMBLER == Assembler.GAS) {
			STABS_endfunction();
			out.println(".size " + ProceedCompiler.censor2(currentFunctionName) + ", .-" + ProceedCompiler.censor2(currentFunctionName));
		}
	}
	
	public void initStack() {
		stackInited = true;
		
		//if (vars.size() > 0) {
			out.println("	push	" + Registers.BP + "\n"+
						"	mov	" + Registers.BP + ", " + Registers.SP + "");
		
			out.println("	sub	" + Registers.SP + ", " + vars.size()*Registers.REGISTER_SIZE);
		//}
		//else if (!Registers.ARCH.equals("amd64")) out.println("	push	" + Registers.BX + "");
	}
	public void resetStack() {
		stackInited = false;
		
		
		/*if (vars.size() > 0)*/ out.println(
				"	mov	" + Registers.SP + ", " + Registers.BP + "\n"+
				"	pop	" + Registers.BP + "");
		//else if (!Registers.ARCH.equals("amd64")) out.println("	pop	" + Registers.BX + "");
	}
	
	// Comments
	
	public void comment(String text) {
		if (ASSEMBLER == Assembler.GAS) out.println("\t/* " + text.replace("/*", "/ *").replace("*/", "* /") + " */");
		else out.println("\t; " + text);
	}
	
	public void space() {
		out.println();
	}
	
	/**
	 * Lisää assemblyyn debug-tietoa
	 */
	public void line(int line) {
		comment("line " + line);
		
		if (ASSEMBLER == Assembler.GAS) {
			out.println(".L" + FUNCTION_COUNTER + "DL" + line + ":");
			STABS_line(line, ".L" + FUNCTION_COUNTER + "DL" + line);
		}
	}

	// Top-level static
	
	public static void start(File file, ArrayList<String> types, ArrayList<String[]> complexTypes) {
		out = ProceedCompiler.out;
		Stor.types = types;
		
		if (ASSEMBLER == Assembler.GAS) {
			out.println(".intel_syntax noprefix");
			out.println(".file \""+file.getName()+"\"");
			STABS_start(file, types, complexTypes);
			out.println(".text");
		}
	}
	
	public static void end() {
		if (ASSEMBLER == Assembler.GAS) {
			STABS_end();
		}
	}
	
	public static void global(ProceedCompiler comp, String name) {
		if (ASSEMBLER == Assembler.GAS)
			out.println(".global " + ProceedCompiler.censor2(name));
		else out.println("global " + ProceedCompiler.censor2(name));
	}
	
	public static void extern(ProceedCompiler comp, String name) {
		if (ASSEMBLER == Assembler.GAS)
			out.println(".extern " + ProceedCompiler.censor2(name));
		else out.println("extern " + ProceedCompiler.censor2(name));
	}
	
	public static void segment(String name) {
		if (ASSEMBLER == Assembler.NASM)
			out.println("segment " + name);
		else out.println(".section " + name);
	}
	
	public static void constant(ProceedCompiler comp, String name, List<String> data) {
		if (ASSEMBLER == Assembler.GAS) {
			out.print("" + ProceedCompiler.censor2(name) + ":\n\t.ascii \"");
			out.print("\\x" + data.get(0).substring(2));
			for (int i = 1; i < data.size(); i++) {
				out.print("\\x" + data.get(i).substring(2));
			}
			out.println("\"");
		} else {
		
			out.print("" + ProceedCompiler.censor2(name) + ":\n\tconst"+(comp.ccounter++)+" db ");
			out.print(data.get(0));
			for (int i = 1; i < data.size(); i++) {
				out.print(", " + data.get(i));
			}
			out.println();
		}
	}
	
	public static void staticdata(ProceedCompiler comp, String name) {
		if (ASSEMBLER == Assembler.GAS) out.println(".comm " + ProceedCompiler.censor2(name) + "," + Registers.REGISTER_SIZE);
		else out.println("" + ProceedCompiler.censor2(name) + " resw " + Registers.REGISTER_SIZE_W);
	}
	
	// Debug information (STABS for GAS)
	
	private static String STABS_currentBlock = "";
	private static int STABS_currentBlockNumber = 1;
	
	private static final int STABS_typeIdShift = 3;
	
	/**
	 * Kirjaa STABS-formaatissa tietoja käännöksestä, mukaanlukien lähdekooditiedostot ja tietotyypit
	 * 
	 * @param file Lähdekooditiedosto
	 * @param types Yksinkertaiset tyypit
	 * @param complexTypes Tyyppiargumentoidut tyypit
	 */
	public static void STABS_start(File file, ArrayList<String> types, ArrayList<String[]> complexTypes) {
		
		/* Lähdekooditiedoston asetukset */
		out.println(".stabs \""+file.getAbsolutePath().substring(0, file.getAbsolutePath().length()-file.getName().length())+"\",100,0,0,Ltext0");
		out.println(".stabs \""+file.getName()+"\",100,0,0,Ltext0");
		
		out.println(".text");
		out.println("Ltext0:");
		out.println(".stabs  \"pilc_compiled.\",60,0,0,0");
		
		/* Yksinkertaiset tietotyypit */
		
		/* Kokonaislukujen suuruudet */
		String range = Registers.ARCH.equals("x86") ? "-2147483648;2147483647" : "-4294967296;4294967295";
		
		/* Anyt, voivat säilöä mitä tahansa. _Any8_ on tavu, _AnyXX_ on kokonaisluku */
		out.println(".stabs  \"_Any"+Registers.REGISTER_SIZE_BIT+"_:t1=r1;"+range+";\",128,0,0,0");
		out.println(".stabs  \"_Any8_:t2=r2;0;127;\",128,0,0,0");
		
		@SuppressWarnings("unchecked")
		ArrayList<String> simpleTypes = (ArrayList<String>) types.clone();	
		
		for (int i = 0; i < complexTypes.size(); i++) {
			if (simpleTypes.indexOf(complexTypes.get(i)[0]) != -1)
				simpleTypes.remove(simpleTypes.indexOf(complexTypes.get(i)[0]));
		}
		
		for (int i = 0; i < simpleTypes.size(); i++) {
			// Tyypin range
			String type = "r"+(types.indexOf(simpleTypes.get(i))+ STABS_typeIdShift)+";"+range+"";
			
			// FIXME: kovakoodausta...
			if (simpleTypes.get(i).equals("String")) type = "*2";
			if (simpleTypes.get(i).startsWith("Function<")) type = "*f1";
			if (simpleTypes.get(i).startsWith("Pointer<")) {
				String typename = simpleTypes.get(i);
				typename = typename.substring("Pointer".length()+1, typename.length()-1);
				
				type = "*" + (types.indexOf(typename) + STABS_typeIdShift);
			}
			if (simpleTypes.get(i).startsWith("List<")) {
				String typename = simpleTypes.get(i);
				typename = typename.substring("List".length()+1, typename.length()-1);
				typename = "Integer"; // PHL-listoissa ensimmäinen arvo on aina koko... TODO kovakoodausta
				
				type = "*ar1;0;10;" + (types.indexOf(typename) + STABS_typeIdShift);
			}
			
			out.println(".stabs  \""+simpleTypes.get(i).replace(":", "::")+":t"+(types.indexOf(simpleTypes.get(i))+ STABS_typeIdShift)+"="+type+";\",128,0,0,0");
		}
		
		/* Tyyppiargumentoidut tyypit, korvaa esiintymät kuten !Tyyppi? tyypin STABS-esityksellä */
		for (int i = 0; i < complexTypes.size(); i++) {
			String complex = complexTypes.get(i)[1];
			for (int j = 0; j < types.size(); j++) complex = complex.replace("!" + types.get(j) + "?", ""+(j+ STABS_typeIdShift));
			complex = complex.replaceAll("\\!.*\\?", "1");
			
			out.println(".stabs  \""+complexTypes.get(i)[0].replace(":", "::")+":t"+(types.indexOf(complexTypes.get(i)[0])+ STABS_typeIdShift)+"="+complex+"\",128,0,0,0");
		}
	}
	
	/**
	 * Kirjaa 
	 */
	private static void STABS_end() {
		out.println(".stabs \"\",100,0,0,.Letext0");
		out.println(".Letext0:");
		out.println(".ident \"PILC " + ProceedParser.PIL_VERSION + "\"");
	}
	
	private void STABS_function(String name) {
		out.println(".stabs \""+STABS_demanglefunction(name)+":F1\",36,0,0,"+ProceedCompiler.censor2(name)+"");      // 36 is N_FUN
		
		for (int i = 0; i < args.size(); i++) {
			String offset = "";
			if (Registers.ARCH.equals("amd64")) {
				if (i < 6);
				else offset = ""+((i-6)*Registers.REGISTER_SIZE+Registers.REGISTER_SIZE*(args.size()>0?2:1));
			} else offset =  ""+(i*Registers.REGISTER_SIZE+Registers.REGISTER_SIZE*(args.size()>0?2:1));
			if (offset.length() > 0) out.println(".stabs \""+STABS_demanglevar(args.get(i))+":p1\",160,0,0," + offset);
			else if (vars.contains(args.get(i))) {
				out.println(".stabs \""+STABS_demanglevar(args.get(i))+":p"+STABS_getType(args.get(i))+"\",160,0,0,-" + (vars.indexOf(args.get(i))*Registers.REGISTER_SIZE+Registers.REGISTER_SIZE));
				// TODO korjaa parametrit --- alla määrittelee parametrin "paikalliseksi muuttujaksi"
				//out.println(".stabs \""+STABS_demanglevar(args.get(i))+":"+STABS_getType(args.get(i))+"\",128,0,0,-" + (vars.indexOf(args.get(i))*Registers.REGISTER_SIZE+Registers.REGISTER_SIZE));
			}
			else {
				out.println(".stabs \""+STABS_demanglevar(args.get(i))+":P"+STABS_getType(args.get(i))+"\",64,0,0," + (args.size()-1-i));
			}
		}
		
		out.println((STABS_currentBlock = ".L" + FUNCTION_COUNTER + "DBS" + ++STABS_currentBlockNumber) + ":");
	}
	
	private String STABS_getType(String var) {
		if (vartypes.containsKey(var)) {
			if (types.contains(vartypes.get(var))) return ""+(types.indexOf(vartypes.get(var))+ STABS_typeIdShift);
		}
		return "1";
	}

	private void STABS_endfunction() {
		for (int i = 0; i < vars.size(); i++) {
			if (args.contains(vars.get(i))) continue;
			
			out.println(".stabs \""+STABS_demanglevar(vars.get(i))+":"+STABS_getType(vars.get(i))+"\",128,0,0,-" + (i*Registers.REGISTER_SIZE+Registers.REGISTER_SIZE));
		}
		
		String functionStart = (".L" + FUNCTION_COUNTER + "DBS" + STABS_currentBlockNumber);
		String functionEnd = (".L" + FUNCTION_COUNTER + "DBE" + STABS_currentBlockNumber--);
		
		out.println(".stabn 192,0,0," + functionStart + "-" + functionStart);
		out.println(".stabn 224,0,0," + functionEnd + "-" + functionStart);
		out.println(functionEnd + ":");
		
		
		STABS_currentBlock = "";
	}
	
	private void STABS_line(int num, String label) {
		out.println(".stabn 0x44,0,"+num+", " + label + "-" + STABS_currentBlock);      // 0x44 is N_SLINE
	}
	
	private String STABS_demanglefunction(String function) {
		if (!function.startsWith("function@")) return "_E" + function;
		
		if (function.startsWith("function@method@") || function.startsWith("function@vmethod@")) {
			function = function.substring(function.indexOf("@")+1);
			function = function.substring(function.indexOf("@")+1);
			String clazz = function.split("\\.")[0];
			String method = function.split("\\.")[1];
			if (method.startsWith("operator@")) method = demangleOperator(method).replace(":", "::");
			return clazz + "." + method;
		}
		
		String censored = ProceedCompiler.censor2(function);
		if (censored.contains("_")) return censored.substring(censored.indexOf("_")+1);
		else return censored;
	}
	
	public static String demangleOperator(String func) {
		String a = "operator ";
		
		String op = func.substring(9);
		String[] chs = op.split("_");
		for (String s1 : chs) {
			a += ((char)org.kaivos.proceed.parser.NumberParser.parseHex(s1));
		}
		return a;
	}
	
	private String STABS_demanglevar(String var) {
		if (var.startsWith("_")) return "_T" + var;
		if (var.startsWith("var@")) return var.substring(4);
		return var;
	}
	
	// Not supported
	
	public void callm(String to, String label) {
		throw new RuntimeException("Supported only in C-mode!");
		
	}

	public void callPtrm(String to, String label) {
		throw new RuntimeException("Supported only in C-mode!");
	}
	
}