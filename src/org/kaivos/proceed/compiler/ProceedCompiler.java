package org.kaivos.proceed.compiler;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.kaivos.proceed.parser.ProceedTree.ConstTree;
import org.kaivos.proceed.parser.ProceedTree.FunctionTree;
import org.kaivos.proceed.parser.ProceedTree.LineTree;
import org.kaivos.proceed.parser.ProceedTree.LineTree.Type;
import org.kaivos.proceed.parser.ProceedTree.StartTree;

/**
 * PIL-kääntäjä. Tukee seuraavia muotoja: x86 tai amd64 assembly NASM tai GAS -syntaksilla, C-koodi
 * @author Iikka Hauhio
 *
 */
public class ProceedCompiler {

	public String func;

	public HashMap<String, FunctionTree> functions = new HashMap<>();
	public HashSet<String> statics = new HashSet<>();
	public HashSet<String> externs = new HashSet<>();
	public HashSet<String> extern_pils = new HashSet<>();

	/**
	 * Näiden nimiä ei muunneta -- näitä voi käyttää myös muissa ohjelmissa
	 */
	public HashSet<String> exportables = new HashSet<>();
	
	int ccounter;
	
	public static boolean C = false;
	
	// Output
	
	public static PrintWriter out;
	
	public String censor(String name) {
		if (externs.contains(name)) return name;
		if (exportables.contains(name)) return name;
		return censor2(name);
	}
	
	/**
	 * 
	 * <p>Supistaa PHL-nimeä ja poistaa @-merkit.</p>
	 * 
	 * <p>PHL-nimi on muotoa<p>
	 * <tt>{<i>lippu@</i>}[<i>Luokka.</i>{<i>nimilippu@</i>}]<i>nimi</i></tt>
	 * 
	 * <p>Jokainen lippu ja nimilippu korvataan lyhenteellä valmiin nimen alussa. Lippujen jälkeen tuleva numero kertoo _-merkkien lukumäärän luokan nimessä. Lippuja ja nimeä erottaa _-merkki.</p>
	 * 
	 * <p>Esimerkiksi nimi <tt>function@method@Car.setfield@motor</tt> muutetaan muotoon <tt>fms0_Car_motor</tt>.</p>
	 * 
	 * <p>Tuetut liput:</p>
	 * <pre>
	 * function   => f
	 * method     => m
	 * vmethod    => v
	 * autocast   => A
	 * manualcast => M
	 * modulef    => F
	 * </pre>
	 * 
	 * <p>Tuetut nimiliput:</p>
	 * <pre>
	 * setfield   => s
	 * operator   => o
	 * method     => t
	 * getref     => r
	 * </pre>
	 * @param name Sensuroitava nimi
	 * @return Sensuroitu nimi
	 */
	public static String censor2(String name) {
		String a = "";
		
		String[] info = name.split("@");
		for (int i = 0; i < info.length; i++) {
			String in = info[i];
			
			if (in.equals("function")) a+="f";
			else if (in.equals("method")) a+="m";
			else if (in.equals("vmethod")) a+="v";
			else if (in.equals("autocast")) a+="A";
			else if (in.equals("manualcast")) a+="M";
			else if (in.equals("modulef")) a+="F";
			else if (in.contains(".")) {
				String clazz = in.split("\\.")[0];
				String method = in.split("\\.")[1];
				
				int viivat = 0, index = 0;
				while ((index = clazz.indexOf('_', index)) != -1) viivat++;
				
				if (i != info.length-1) {
					if (method.equals("setfield")) a += "s";	
					else if (method.equals("operator")) a += "o";
					else if (method.equals("method")) a += "t";
					else if (method.equals("getref")) a += "r";
					else a += method;
					
					a += viivat + "_" + clazz + "_" + info[++i];
				}
				else {
					a += viivat + "_" + clazz + "_" + method;
				}
			}
			else {
				if (i != 0) a += "_";
				a += in;
			}
			
		}
		
		return a;
	}
	
	public void global(String name) {
		if (C) StorC.global(this, name);
		
		else Stor.global(this, name);
	}
	
	public void extern(String name) {
		if (C) {
			
			StorC.extern(this, name);
		}
		
		else Stor.extern(this, name);
	}
	
	public void segment(String name) {
		if (!C)
			Stor.segment(name);
	}
	
	public void constant(String name, List<String> data) {
		if (C) {
			StorC.constant(this, name, data);
		}
		
		else {
			Stor.constant(this, name, data);
		}
	}
	
	public void staticdata(String name) {
		if (C) StorC.staticdata(this, name);
		else
			Stor.staticdata(this, name);
	}
	
	
	
	/// Compiler
	
	public void compile(StartTree tree, File file) throws CompilerError {
		
		if (C) {
			StorC.start(tree.file != null ? new File(tree.file) : file, tree.types, tree.manualtypes);
			
		} else Stor.start(tree.file != null ? new File(tree.file) : file, tree.types, tree.manualtypes);
		
		for (String s : tree.externs) {
			externs.add(s);
			extern(s);
		}
		for (String s : tree.extern_pils) {
			extern_pils.add(s);
			extern(s);
		}
		for (String s : tree.statics) {
			global(s);
			statics.add(s);
		}
		for (FunctionTree t : tree.functions) {
			if (!functions.containsKey(t.name)) {
				functions.put(t.name, t);
				if (t.export) exportables.add(t.name);
				if (C) StorC.func(this, t.name, t);
				else global(t.name);
			} else {
				throw new CompilerError("!E ("+t.name+") Function " + t.name + " already exists!");
			}
		}
		
		segment(".data");
		
		for (ConstTree s : tree.consts) {
			constant(s.name, s.values);
		}
		
		if (tree.statics.size() != 0) {
			segment(".bss");
			
			for (String s : tree.statics) {
				staticdata(s);
				statics.add(s);
			}
		}
		segment(".text");
		
		for (FunctionTree t : tree.functions) {
			compileFunction(t);
		}
		
		Stor.end();
	}

	/**
	 * Tallennetut aliakset
	 */
	HashMap<String, String> constantValues = new HashMap<>();
	HashMap<String, String> constantValues_r = new HashMap<>(); // TODO Selvitä, mikä tämän idea on
	
	private void compileFunction(FunctionTree tree) throws CompilerError {
		
		func = tree.name;
		
		constantValues.clear();
		constantValues_r.clear();
		
		boolean containsVariables = false;
		
		ArrayList<String> vars = new ArrayList<>();
		
		Map<String, Integer> labels = new HashMap<>();
		int lcount = 0;
		
		for (LineTree t : tree.lines) {
			if (t.type == Type.ASSIGN || t.type == Type.ASSIGNF || t.type == Type.READ || t.type == Type.ALIASF) {
				if (!vars.contains(t.var) && !tree.params.contains(t.var) && !statics.contains(t.var) && !t.var.startsWith("@") && !t.var.matches("o[abcd]x")) vars.add(t.var);
			}
			if (t.type == Type.LABEL) labels.put(t.var, lcount++);
		}
		
		// 64-bittisen arkkitehtuurin prototyyppi
		if (Registers.ARCH.equals("amd64") && !C && !tree.argreg) {
			vars.addAll(tree.params.subList(0, Math.min(6, tree.params.size()))); // lisää rekisteriargumentit vars-listaan
		}
		
		containsVariables = vars.size() > 0;
		
		Stor st = C ? new StorC(this) : new Stor(this);
		st.vars = vars;
		
		st.vartypes = tree.vartypes;
		
		st.args = tree.params;
		st.argreg = tree.argreg;
		
		// TODO paranna kutsumismekanismia
		//if (!Registers.ARCH.equals("amd64") || C || tree.argreg) st.args = tree.params; // 64-bittisessä tilassa rekisteriargumentit (6 ekaa) laitetaan pinoon ja löytyvät vars-listasta
		//else if (tree.params.size() >= 6) st.args = (tree.params.subList(6, tree.params.size()));
		
		st.function(tree.name, tree.varargs);
		
		if (containsVariables || true) {
		
			for (String var : vars) {
				st.comment("" + var + " = " + st.getVar(var));
			}
			
			if (!Registers.ARCH.equals("amd64") || C || tree.argreg)
				for (String var : tree.params) {
					st.comment("" + var + " = " + st.getVar(var));
				}
			else
				for (String var : tree.params.subList(0, Math.min(6, tree.params.size()))) {
					st.comment("" + var + " = " + st.getVar(var));
				}
			
			st.initStack();
		
			// 64-bittisen arkkitehtuurin prototyyppi
			if (Registers.ARCH.equals("amd64") && !C && !tree.argreg) {
				for (int i = 0; i < Math.min(6, tree.params.size()); i++) {
					st.move(tree.params.get(i), Registers.amd64Arguments[i]);
				}
			}
			
		}
		
		int line = -1;
		
		for (int in = 0; in < tree.lines.size(); in++) {
			LineTree t = tree.lines.get(in);
			
			if (line < t.line && t.line > 0)
				st.line(t.line);
			if (t.line > 0) line = t.line;
			
			st.space();
			switch (t.type) {
			case ASSIGN:
				st.comment("" + t.var + " = " + t.label);
				if (constantValues_r.containsKey(t.var)) constantValues_r.remove(t.var);
				if ((!vars.contains(t.label) && !tree.params.contains(t.label))) constantValues.put(t.var, t.label);
				else if (constantValues.containsKey(t.label)) constantValues.put(t.var, constantValues.get(t.label));
				{
					if (C) {
						st.move(t.var, t.label);
					}
					else try {
						Integer.parseInt(t.label);
						st.move(t.var, t.label);
					} catch (NumberFormatException ex) {
						st.move(Registers.AX, t.label);
						st.move(t.var, Registers.AX);
					}
				}
				break;
			case ALIAS:
				st.comment("alias " + t.var + " = " + t.label);
				constantValues_r.put(t.var, t.label);
				constantValues.put(t.var, t.label);
				break;
			case ALIASF:
			case ASSIGNF:
				if (constantValues_r.containsKey(t.var)) constantValues_r.remove(t.var);
				/*
				constantValues.remove(t.var);
				if (constantValues_r.containsKey(t.var)) constantValues.remove(constantValues_r.get(t.var));
				constantValues_r.remove(t.var);
				*/
				st.comment("" + (t.type==Type.ALIASF?"alias ":"") + t.var + " = " + t.label + t.args);
				if (Arrays.asList("+", "-", "*", "/", "%", "&", "|", "^", "==", "!=", "<", ">", "<=", ">=").contains(t.label)) {
					if (t.label.equals("-") && t.args.size() == 1) {
						t.args.add(0, "0");
					}
					
					// LEA
					if (t.label.equals("&") && t.args.size() == 1) {
						st.lea(Registers.AX, t.args.get(0));
						st.move(t.var, Registers.AX);
						break;
					}
					
					// Jos kaikki laskun arvot ovat tiedossa, laske lasku
					{
						ArrayList<Integer> args = new ArrayList<>();
						for (int i = 0; i < t.args.size(); i++) {
							try {
								args.add(Integer.parseInt(st.getValAddr(t.args.get(i))));
								
							}
							catch (NumberFormatException ex) {}
						}
						
						if (args.size() == t.args.size()) {
							int i = args.remove(0);
							switch (t.label) {
							case "+":
								for (int j:args) i += j;
								break;
							case "-":
								for (int j:args) i -= j;
								break;
							case "*":
								for (int j:args) i *= j;
								break;
							case "/":
								for (int j:args) i /= j;
								break;
							case "%":
								for (int j:args) i %= j;
								break;
							case "&":
								for (int j:args) i &= j;
								break;
							case "|":
								for (int j:args) i |= j;
								break;
							case "^":
								for (int j:args) i ^= j;
								break;
							case "==":
								for (int j:args) i = i == j ? 1 : 0;
								break;
							case "!=":
								for (int j:args) i = i != j ? 1 : 0;
								break;
							case "<":
								for (int j:args) i = i < j ? 1 : 0;
								break;
							case ">":
								for (int j:args) i = i > j ? 1 : 0;
								break;
							case "<=":
								for (int j:args) i = i <= j ? 1 : 0;
								break;
							case ">=":
								for (int j:args) i = i >= j ? 1 : 0;
								break;

							default:
								break;
							}
							if (t.type == Type.ALIASF) constantValues_r.put(t.var, ""+i);
							else {
								st.move(t.var, ""+i);
								constantValues.put(t.var, ""+i);
							}
							break;
						}
					}
					
					// Laskutoimitus
					st.move(Registers.AX, t.args.get(0));
					if ((t.label.equals("/") || t.label.equals("%")) && !C) {
						
						if (!Registers.ARCH.equals("amd64")) st.ins("cdq"); // convert double quad (32 bit -> 64 bit)
						else st.ins("cqo"); // convert quad octal (64 bit -> 128 bit)
						
						st.move(Registers.BX, t.args.get(1));
						st.ins("idiv", Registers.BX);
						if (t.label.equals("/")) {
							st.move(t.var, Registers.AX);
						} else {
							st.move(t.var, Registers.DX);
						}
					} else {
						
						if (t.label.equals("+")) {
							for (int i = 1; i < t.args.size(); i++) st.mins("add", Registers.AX, t.args.get(i));
							
						} else if (t.label.equals("-")) {
							for (int i = 1; i < t.args.size(); i++) st.mins("sub", Registers.AX, t.args.get(i));
							
						} else if (t.label.equals("*")) {
							for (int i = 1; i < t.args.size(); i++) st.mins("imul", Registers.AX, t.args.get(i));
							
						} else if (t.label.equals("&")) {
							for (int i = 1; i < t.args.size(); i++) st.mins("and", Registers.AX, t.args.get(i));
							
						} else if (t.label.equals("|")) {
							for (int i = 1; i < t.args.size(); i++) st.mins("or", Registers.AX, t.args.get(i));
							
						} else if (t.label.equals("^")) {
							for (int i = 1; i < t.args.size(); i++) st.mins("xor", Registers.AX, t.args.get(i));
							
						} else if (t.label.equals("/")) {
							for (int i = 1; i < t.args.size(); i++) st.mins("_div_", Registers.AX, t.args.get(i));
							
						} else if (t.label.equals("%")) {
							for (int i = 1; i < t.args.size(); i++) st.mins("_mod_", Registers.AX, t.args.get(i));
							
						} else if (t.label.equals("==")) {
							for (int i = 1; i < t.args.size(); i++) {
								if (C) st.mins("_eq_", Registers.AX, t.args.get(i));
								else {
									st.mins("cmp", Registers.AX, t.args.get(i));
									st.ins("sete", ":al");
									st.mins("movzx", Registers.AX, ":al");
								}
							}
							
						} else if (t.label.equals("!=")) {
							for (int i = 1; i < t.args.size(); i++) {
								if (C) st.mins("_neq_", Registers.AX, t.args.get(i));
								else {
									st.mins("cmp", Registers.AX, t.args.get(i));
									st.ins("setne", ":al");
									st.mins("movzx", Registers.AX, ":al");
								}
							}
							
						} else if (t.label.equals("<")) {
							for (int i = 1; i < t.args.size(); i++) {
								if (C) st.mins("_lt_", Registers.AX, t.args.get(i));
								else {
									st.mins("cmp", Registers.AX, t.args.get(i));
									st.ins("setl", ":al");
									st.mins("movzx", Registers.AX, ":al");
								}
							}
							
						} else if (t.label.equals(">")) {
							for (int i = 1; i < t.args.size(); i++) {
								if (C) st.mins("_gt_", Registers.AX, t.args.get(i));
								else {
									st.mins("cmp", Registers.AX, t.args.get(i));
									st.ins("setg", ":al");
									st.mins("movzx", Registers.AX, ":al");
								}
							}
							
						} else if (t.label.equals("<=")) {
							for (int i = 1; i < t.args.size(); i++) {
								if (C) st.mins("_le_", Registers.AX, t.args.get(i));
								else {
									st.mins("cmp", Registers.AX, t.args.get(i));
									st.ins("setle", ":al");
									st.mins("movzx", Registers.AX, ":al");
								}
							}
							
						} else if (t.label.equals(">=")) {
							for (int i = 1; i < t.args.size(); i++) {
								if (C) st.mins("_ge_", Registers.AX, t.args.get(i));
								else {
									st.mins("cmp", Registers.AX, t.args.get(i));
									st.ins("setge", ":al");
									st.mins("movzx", Registers.AX, ":al");
								}
							}
							
						} else {
							throw new CompilerError("Unknown operator: " + t.label);
						}
						if (!t.var.equals("oax"))
						st.move(t.var, Registers.AX);
					}
				} else {
					
					// sisäänrakennetut funktiot
					if (t.label.equals("@") && t.args.size() == 1) { // "get" --- tämä ja "set" ovat myös proceed-komennolla
						st.move("oax", t.args.get(0));
						st.move(Registers.BX, "@oax");
						st.move(t.var, Registers.BX);
						break;
					}
					if (t.label.equals("get") && t.args.size() == 2) { // "get" --- tämä ja "set" ovat myös proceed-komennolla
						st.move("oax", t.args.get(1));
						st.mins("imul", "oax", "osize");
						st.mins("add", "oax", t.args.get(0));
						st.move(Registers.AX, "@oax");
						st.move(t.var, Registers.AX);
						break;
					}
					
					// Funktio
					
					{
					
						for (int i = t.args.size()-1, j = 0; i >= 0; i--, j++) {
							st.pushArg(i, t.args.get(i));
						}
						constantValues.clear();
						if (C) {
							if (functions.containsKey(t.label)) st.callm(t.var, t.label);
							else st.callPtrm(t.var, t.label);
							st.clearArgs(t.args.size());
						}
						else {
							if (!functions.containsKey(t.label) && !externs.contains(t.label)) {
								st.move(Registers.AX, t.label);
								st.callPtr(Registers.AX);
							} else {
								st.mins("xor", Registers.AX, Registers.AX);
								st.call(t.label);
							}
							st.clearArgs(t.args.size());
							
							if (in < tree.lines.size()-1 && tree.lines.get(in+1).type == Type.RETURN && tree.lines.get(in+1).var.equals(t.var))
								tree.lines.remove(in+1); // Poista return, arvo on jo eax:ssä
							else {
								st.move(t.var, Registers.AX); // Laita arvo variin
							}
						}
					}
				}
				break;
			case GOTO:
				constantValues.clear();
				st.jump("" + labels.get(t.label));
				break;
			case CGOTO:
				constantValues.clear();
				st.jump(t.test, "" + labels.get(t.label));
				break;
			case LABEL:
				constantValues.clear();
				st.label("" + labels.get(t.var));
				break;
			case IF:
			case IFN:
				st.move(Registers.AX, t.var);
				st.ins("cmp", Registers.AX, t.var2);
				constantValues.clear();
				st.jump(t.test, "" + labels.get(t.label));
				break;
			case CMP:
				st.ins("cmp", t.var2, t.var);
				constantValues.clear();
				st.jump(t.test, "" + labels.get(t.label));
				break;
			case RETURN:
				st.comment("return " + t.var);
				st.move(Registers.AX, t.var);
				break;
			case PUT:
				st.comment("put " + t.var2 + " " + (t.arg?"arg ":"") + t.var);
				st.move(t.var2, (t.arg?"arg$":"") + t.var);
				constantValues.clear();
				break;
			case READ:
				st.comment("read " + t.var + " " + (t.arg?"arg ":"") + t.var2);
				st.move(t.var, (t.arg?"arg$":"") + t.var2);
				constantValues.clear();
				break;
			case PROCEED:
				st.comment("proceed " + t.label + t.args);
				
				if (t.label.equals("@") && t.args.size() == 2) { // "set"
					st.pset(t.args.get(0), t.args.get(1));
					break;
				}
				if (t.label.equals("@") && t.args.size() == 1) { // "get"
					st.move("oax", t.args.get(0));
					st.move(Registers.AX, "@oax");
					break;
				}
				if (t.label.equals("set") && t.args.size() == 3) { // "set"
					if (!t.args.get(1).equals("0")) {
						st.move("oax", t.args.get(1));
						st.mins("imul", "oax", "osize");
						st.mins("add", "oax", t.args.get(0));
					}
					else st.move("oax", t.args.get(0));
					st.move(Registers.BX, t.args.get(2));
					st.move("@oax", Registers.BX);
					break;
				}
				if (t.label.equals("get") && t.args.size() == 2) { // "get"
					if (!t.args.get(1).equals("0")) {
						st.move("oax", t.args.get(1));
						st.mins("imul", "oax", "osize");
						st.mins("add", "oax", t.args.get(0));
					}
					else st.move("oax", t.args.get(0));
					st.move(Registers.AX, "@oax");
					break;
				}
				
				// Pil-funktioita voi kutsua laittamalla argumentit pinoon
				if (((functions.containsKey(t.label) || extern_pils.contains(t.label)) || !t.cconv) && !C && false) {
					for (int i = t.args.size()-1; i >= 0; i--) {
						st.push(t.args.get(i));
					}
					constantValues.clear();
					if (!functions.containsKey(t.label) && !externs.contains(t.label)) {
						st.move(Registers.AX, t.label);
						st.callPtr(Registers.AX);
					} else {
						st.call(t.label);
					}
					st.mins("sub", Registers.SP, ""+(t.args.size()*Registers.REGISTER_SIZE));
				
				// muut funktiot kutsutaan laittamalla argumentit rekistereihin (64-bit)
				} else {
					for (int i = t.args.size()-1; i >= 0; i--) {
						st.pushArg(i, t.args.get(i));
					}
					constantValues.clear();
					if (C) {
						if (functions.containsKey(t.label)) st.call(t.label);
						else st.callPtr(t.label);
					}
					else if (!functions.containsKey(t.label) && !externs.contains(t.label)) {
						st.move(Registers.AX, t.label);
						st.callPtr(Registers.AX);
					} else {
						st.mins("xor", Registers.AX, Registers.AX);
						st.call(t.label);
					}
					st.clearArgs(t.args.size());
				}
				break;
			case ASM:
				st.comment("DASM STATEMENT");
				out.println("\t" + t.label.substring(1, t.label.length()-1));
				break;

			default:
				break;
			}
		}
		
		st.space();
		
		
		
		if (containsVariables || true) st.resetStack();
		
		st.ret();
	}

}
