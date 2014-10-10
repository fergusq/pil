package org.kaivos.proceed.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.kaivos.lib.ArgumentParser;
import org.kaivos.proceed.compiler.CompilerError;
import org.kaivos.proceed.compiler.ProceedCompiler;
import org.kaivos.proceed.compiler.Registers;
import org.kaivos.proceed.compiler.Stor;
import org.kaivos.proceed.compiler.Stor.Assembler;
import org.kaivos.sc.TokenScanner;
import org.kaivos.stg.error.SyntaxError;
import org.kaivos.stg.error.UnexpectedTokenSyntaxError;

public class ProceedParser 
{
	
	public static final String PIL_VERSION = "1.2.6";
	
	public static final String COMPILER_NAME = "Proceed Intermediate Language Compiler";
	
	public static final String COMPILER_MINOR = "b";
	public static final String COMPILER_VERSION = "#pilc" + createVersion(PIL_VERSION) + COMPILER_MINOR;
	
	private static String createVersion(String phlversion) {
		String[] numbers = phlversion.split("\\.");
		String a = "";
		for (int i = 0; i < numbers.length; i++) {
			a += (numbers[i].length()<3?mul(3-numbers[i].length(), "0")+numbers[i]:numbers[i]);
		}
		
		return a;
	}
	
	private static String mul(int i, String string) {
		String a = "";
		for (int j = 0; j < i; j++) a += string;
		return a;
	}
	
	private static Map<String, Integer> argtypes = new HashMap<>();
	static {
		argtypes.put("i", 0);
		argtypes.put("a", 0);
		argtypes.put("target", 1);
		argtypes.put("arch", 1);
		argtypes.put("out", 1);
		argtypes.put("manglec", 1);
		
		argtypes.put("h", 0);
		argtypes.put("help", 0);
		argtypes.put("v", 0);
		argtypes.put("version", 0);
	}
	
	public static void main(String[] args) {
		ArgumentParser a = new ArgumentParser(argtypes, args);
		
		if (a.getFlag("v") != null || a.getFlag("version") != null)
		{
			System.out.println(COMPILER_NAME + " " + PIL_VERSION + COMPILER_MINOR);
			System.out.println(COMPILER_VERSION);
			return;
		}
		
		if (a.getFlag("h") != null || a.getFlag("help") != null) {
			System.out.println("Usage: java -cp phl.jar org.kaivos.proceed.parser.ProceedParser [OPTIONS] file");
			System.out.println("| java -cp phl.jar org.kaivos.proceed.parser.ProceedParser [OPTIONS]  -i");
			System.out.println("Options:");
			System.out.println("--target t  set the target. possibilities are: nasm, gas, c.");
			System.out.println("--arch a    set the target arch. possibilities are: x86, amd64.");
			System.out.println("--out o     set the output file");
			System.out.println("-a          compile and assemble");
			System.out.println("--version   print compiler version");
			System.out.println("-v          print compiler version");
			return;
		}
		
		if (a.getFlag("manglec") != null) {
			String name = a.getFlag("manglec").getFlagArgument();
			System.out.println(new ProceedCompiler().censor(name));
			return;
		}
		
		BufferedReader in = null;
		
		File f = new File(a.lastText());
		
		if (a.getFlag("i") != null) {
			in = new BufferedReader(new InputStreamReader(System.in));
		} else {
			try {
				in = new BufferedReader(new FileReader(f));
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
				return;
			}
		}
		if (a.getFlag("arch") != null) {
			switch (a.getFlag("arch").getFlagArgument().toLowerCase()) {
			case "x86":
				Registers.toX86();
				ProceedCompiler.C = false;
				break;
			case "amd64":
				Registers.toAmd64();
				ProceedCompiler.C = false;
				break;

			default:
				break;
			}
		}
		
		Stor.ASSEMBLER = Assembler.NASM;
		ProceedCompiler.C = false;
		
		if (a.getFlag("target") != null) {
			switch (a.getFlag("target").getFlagArgument().toLowerCase()) {
			case "nasm":
				Stor.ASSEMBLER = Assembler.NASM;
				ProceedCompiler.C = false;
				break;
			case "gas":
				Stor.ASSEMBLER = Assembler.GAS;
				ProceedCompiler.C = false;
				break;
			case "c":
				Stor.ASSEMBLER = Assembler.C;
				ProceedCompiler.C = true;
				break;

			default:
				Stor.ASSEMBLER = Assembler.GAS;
				ProceedCompiler.C = false;
				break;
			}
		}
		
		String out = (a.getFlag("a") == null) ? f.getName() + ".asm" : f.getName() + ".o";
		
		if (a.getFlag("out") != null) {
			out = a.getFlag("out").getFlagArgument();
		}
		
		String textIn = "";
		try {
			while (in.ready())
				textIn += in.readLine() + "\n";
		} catch (IOException e1) {
			e1.printStackTrace();
			return;
		}

		TokenScanner s = new TokenScanner();
		s.setSpecialTokens(new char[] { ';', '<', '>', '(', ')', ',', ':', '+',
				'-', '*', '/', '%', '=', '&', '|', '{', '}', '!', '[',
				']', '$' });
		s.setBSpecialTokens(new String[] { "->", "=>", "==", "!=", "&&", "||",
				"<=", ">=", "++", "--", });
		s.setComments(true);
		s.setPrep(true);
		s.setFile(a.lastText());
		s.init(textIn);
		// System.out.println(s.getTokenList());
		ProceedTree.StartTree tree = new org.kaivos.proceed.parser.ProceedTree.StartTree();

		try {
			tree.parse(s);
			try {
				in.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			ProceedCompiler compiler = new ProceedCompiler();
			try {
				if (a.getFlag("a") == null) {
					ProceedCompiler.out = new PrintWriter(new File(out));
					compiler.compile(tree, f);
					ProceedCompiler.out.flush();
					ProceedCompiler.out.close();
				}
				else {
					File tmp = File.createTempFile("pilc", null);
					
					ProceedCompiler.out = new PrintWriter(tmp);
					compiler.compile(tree, f);
					ProceedCompiler.out.flush();
					ProceedCompiler.out.close();
					
					Process pr = null;
					if (Stor.ASSEMBLER == Assembler.NASM) {
						pr = Runtime.getRuntime().exec(new String[] {"nasm", "-f", "elf" + Registers.REGISTER_SIZE_BIT, "-o", out, tmp.getAbsolutePath()});
					} else if (Stor.ASSEMBLER  == Assembler.GAS) {
						pr = Runtime.getRuntime().exec(new String[] {"as", "--" + Registers.REGISTER_SIZE_BIT, "-o", out, tmp.getAbsolutePath()});
					} else if (Stor.ASSEMBLER == Assembler.C) {
						pr = Runtime.getRuntime().exec(new String[] {"gcc", "-x", "c", "-g", "-w", "-m" + Registers.REGISTER_SIZE_BIT, "-o", out, tmp.getAbsolutePath()});
					} else {
						throw new CompilerError("Assembler not found!");
					}
					
					pr.waitFor();
					
					Scanner sa = new Scanner(pr.getErrorStream());
					Scanner sa2 = new Scanner(pr.getInputStream());
					while (sa.hasNext() || sa2.hasNext()) {
						if (sa.hasNext()) System.err.println(sa.nextLine());
						if (sa2.hasNext()) System.out.println(sa2.hasNext());
					}
					sa.close();
					sa2.close();
					
					tmp.deleteOnExit();
				}
				
				
				
			} catch (CompilerError e) {
				System.err.println(e.msg);
			} catch (Exception e) {
				System.err.println("; E: ("+compiler.func+") Internal Compiler Exception");
				e.printStackTrace();
			}
		} catch (UnexpectedTokenSyntaxError e) {

			if (e.getExceptedArray() == null) {
				System.err.println("[" + e.getFile() + ":" + e.getLine()
						+ "] Syntax error on token '" + e.getToken()
						+ "', excepted '" + e.getExcepted() + "'");
				 //e.printStackTrace();
			} else {
				System.err.println("[" + e.getFile() + ":" + e.getLine()
						+ "] Syntax error on token '" + e.getToken()
						+ "', excepted one of:");
				for (String token : e.getExceptedArray()) {
					System.err.println("[Line " + e.getLine() + "] \t\t'"
							+ token + "'");
				}
			}

			System.err.println("[Line " + e.getLine() + "] Line: '"
					+ s.getLine(e.getLine() - 1).trim() + "'");
		} catch (SyntaxError e) {

			System.err.println("[Line " + e.getLine() + "] " + e.getMessage());
			System.err.println("[Line " + e.getLine() + "] \t"
					+ s.getLine(e.getLine() - 1).trim());
		} catch (StackOverflowError e) {
			System.err.println("Stack overflow exception!");
		}

		{
			// SveCodeGenerator.CGStartTree gen = new
			// SveCodeGenerator.CGStartTree(tree);
			// System.out.println(gen.generate(""));
		}
	}
	
}
