package org.kaivos.proceed.parser;

import java.util.ArrayList;
import java.util.HashMap;

import org.kaivos.parsertools.ParserTree;
import org.kaivos.proceed.compiler.Registers;
import org.kaivos.sc.TokenScanner;
import org.kaivos.stg.error.SyntaxError;

public class ProceedTree extends ParserTree {

	/**
	 * Start = {
	 * 		FUNCTION*
	 * }
	 */
	public static class StartTree extends TreeNode {
		
		public String file;
		public ArrayList<String> types = new ArrayList<>();
		public ArrayList<String[]> manualtypes = new ArrayList<>();
		
		public ArrayList<String> externs = new ArrayList<>();
		public ArrayList<String> extern_pils = new ArrayList<>();
		public ArrayList<ConstTree> consts = new ArrayList<>();
		public ArrayList<String> statics = new ArrayList<>();
		public ArrayList<FunctionTree> functions = new ArrayList<FunctionTree>();
		
		@Override
		public void parse(TokenScanner s) throws SyntaxError {
			while (!seek(s).equals("<EOF>")) {
				if (seek(s).equals("extern")) {
					accept("extern", s);
					externs.add(next(s));
				} else if (seek(s).equals("extern_pil")) {
					accept("extern_pil", s);
					extern_pils.add(next(s));
				} else if (seek(s).equals("const")) {
					ConstTree t = new ConstTree();
					t.parse(s);
					consts.add(t);
				} else if (seek(s).equals("static")) {
					accept("static", s);
					statics.add(next(s));
				} else if (seek(s).equals("%")) {
					accept("%", s);
					String direc = next(s);
					switch (direc) {
					case "file":
						String rawfile = next(s);
						file = rawfile.substring(1, rawfile.length()-1);
						break;
					case "type":
						String type = next(s);
						type = type.substring(1, type.length()-1).replace("@", "");
						types.add(type);
						break;
					case "typedef":
						type = next(s);
						type = type.substring(1, type.length()-1).replace("@", "");
						String manualtype = next(s);
						manualtype = manualtype.substring(1, manualtype.length()-1);
						manualtypes.add(new String [] {type, manualtype});
						break;
						
					default:
						throw new SyntaxError(s.file(), s.line(), "Unknown compiler directive '" + direc + "'");
					}
				} else
					
				{
					FunctionTree t = new FunctionTree();
					t.parse(s);
					functions.add(t);
				}
				continue;
			}
			accept("<EOF>", s);
			
		}

		@Override
		public String generate(String a) {
			return null;
		}
		
	}
	
	/**
	 * Const = {
	 * 		"const" NAME VALUE ("," VALUE)*
	 * }
	 */
	public static class ConstTree extends TreeNode {
		
		public String name;
		public ArrayList<String> values = new ArrayList<>();
		
		@Override
		public void parse(TokenScanner s) throws SyntaxError {
			accept("const", s);
			name = next(s);
			values.add(next(s));
			while (seek(s).equals(",")) {
				accept(",", s);
				values.add(next(s));
			}
		}

		@Override
		public String generate(String a) {
			return null;
		}
		
	}
	
	/**
	 * Function = {
	 * 		LINE*
	 * 		"ret"
	 * }
	 */
	public static class FunctionTree extends TreeNode {
		
		public boolean varargs = false;
		public boolean export = false;
		public boolean argreg = false;
		
		public String name;
		public ArrayList<String> params = new ArrayList<>();
		public ArrayList<LineTree> lines = new ArrayList<LineTree>();
		
		public HashMap<String, String> vartypes = new HashMap<>();
		
		@Override
		public void parse(TokenScanner s) throws SyntaxError {
			if (seek(s).equals("exportable")) {
				accept("exportable", s);
				export = true;
			}
			if (seek(s).equals("varargs")) {
				accept("varargs", s);
				varargs = true;
			}
			if (seek(s).equals("argreg")) {
				accept("argreg", s);
				argreg = true;
			}
			name = next(s);
			accept("(", s);
			if (!seek(s).equals(")")) while (true) {
				params.add(next(s));
				if (accept(new String[]{",", ")"}, s).equals(")")) break;
			} else accept(")", s);
			accept(":", s);
			while (!seek(s).equals("ret")) {
				{
					LineTree t = new LineTree();
					t.parse(s, this);
					lines.add(t);
				}
				continue;
			}
			accept("ret", s);
			
		}

		@Override
		public String generate(String a) {
			return null;
		}
		
	}
	
	/**
	 * Line = {
	 * 		ASSIGN
	 * 		PROCEED
	 * 		GOTO
	 * 		RETURN
	 * }
	 */
	public static class LineTree extends TreeNode {
		
		public ArrayList<String> args = new ArrayList<>();
		
		public String var, var2, test;
		public String label;
		
		public boolean arg = false;
		
		public int line = 0;
		public static boolean manual_lines = false;
		
		/**
		 * Kutsuu funktiota laittamalla arvot rekistereihin (Vain 64-bittinen)
		 */
		public boolean cconv = false;
		
		public enum Type {
			ASSIGN,
			ASSIGNF,
			PROCEED,
			GOTO,
			CGOTO,
			CMP,
			IF,
			IFN,
			LABEL,
			RETURN,
			READ,
			ASM,
			PUT,
			ALIAS, ALIASF
		}
		
		public Type type;
		
		@Override
		public void parse(TokenScanner s) throws SyntaxError {
			parse(s, null);
		}
		
		public void parse(TokenScanner s, FunctionTree f) throws SyntaxError {
			
			if (!manual_lines)
				line = s.nextLine()+1;
			else line = -1;
			
			while (seek(s).equals("%")) {
				accept("%", s);
				String direc = next(s);
				switch (direc) {
				case "line":
					line = Integer.parseInt(next(s));
					manual_lines = true;
					break;
				case "var":
					String type = next(s);
					type = type.substring(1, type.length()-1).replace("@", "");
					if (f != null) f.vartypes.put(next(s), type);
					break;
				case "block_begin":
					break;
				case "block_end":
					break;

				default:
					throw new SyntaxError(s.file(), s.line(), "Unknown compiler directive '" + direc + "'");
				}
			}
			
			if (seek(s).equals("if")) {
				type = accept(new String[]{"if", "ifn"}, s).equals("if") ? Type.IF : Type.IFN;
				test = next(s);
				var = next(s);
				var2 = next(s);
				accept("goto", s);
				label = next(s);
			} else if (seek(s).equals("goto")) {
				accept("goto", s);
				label = next(s);
				type = Type.GOTO;
			} else if (seek(s).equals("j")) {
				accept("j", s);
				test = next(s);
				label = next(s);
				type = Type.CGOTO;
			} else if (seek(s).equals("asm")) {
				accept("asm", s);
				label = next(s);
				type = Type.ASM;
			} else if (seek(s).equals("return")) {
				accept("return", s);
				var = next(s);
				type = Type.RETURN;
			} else if (seek(s).equals("put")) {
				accept("put", s);
				var2 = accept(new String[] {
							Registers.AX,
							Registers.BX,
							Registers.CX,
							Registers.DX,
							Registers.SI,
							Registers.DI,
							Registers.BP,
							Registers.SP,
							
							"oax",
							"obx",
							"ocx",
							"odx",
							"osi",
							"odi",
							"obp",
							"osp"
						}, s);
				if (seek(s).equals("arg")) {
					accept("arg", s);
					arg = true;
					var = accept(new String[] {
							Registers.AX,
							Registers.BX,
							Registers.CX,
							Registers.DX,
							Registers.SI,
							Registers.DI,
							Registers.BP,
							Registers.SP,
							
							"oax",
							"obx",
							"ocx",
							"odx",
							"osi",
							"odi",
							"obp",
							"osp"
						}, s);
				}
				else var = next(s);
				type = Type.PUT;
			} else if (seek(s).equals("read")) {
				accept("read", s);
				var = next(s);
				if (seek(s).equals("arg")) {
					accept("arg", s);
					arg = true;
				}
				var2 = accept(new String[] {
						Registers.AX,
						Registers.BX,
						Registers.CX,
						Registers.DX,
						Registers.SI,
						Registers.DI,
						Registers.BP,
						Registers.SP,
						
						"oax",
						"obx",
						"ocx",
						"odx",
						"osi",
						"odi",
						"obp",
						"osp"
						}, s);
				type = Type.READ;
			} else if (seek(s).equals("cmp")) {
				accept("cmp", s);
				var2 = accept(new String[] {
						Registers.AX,
						Registers.BX,
						Registers.CX,
						Registers.DX,
						Registers.SI,
						Registers.DI,
						Registers.BP,
						Registers.SP,
						
						"oax",
						"obx",
						"ocx",
						"odx",
						"osi",
						"odi",
						"obp",
						"osp"
						}, s);
				var = next(s);
				accept("j", s);
				test = next(s);
				label = next(s);
				type = Type.CMP;
			} else if (seek(s).equals("proceed")) {
				accept("proceed", s);
				label = next(s);
				
				if (seek(s).equals("cconv")) {
					accept("cconv", s);
					cconv = true;
				}
				
				accept("(", s);
				if (!seek(s).equals(")")) while (true) {
					args.add(next(s));
					if (accept(new String[]{",", ")"}, s).equals(")")) break;
				} else accept(")", s);
				type = Type.PROCEED;
			} else if (seek(s).equals("alias")) {
				accept("alias", s);
				var = next(s);
				accept("=", s);
				
				label = next(s);
				
				if (seek(s).equals("cconv")) {
					accept("cconv", s);
					cconv = true;
				}
				
				if (seek(s).equals("(") || cconv) {
					accept("(", s);
					if (!seek(s).equals(")")) while (true) {
						args.add(next(s));
						if (accept(new String[]{",", ")"}, s).equals(")")) break;
					} else accept(")", s);
					type = Type.ALIASF;
				} else type = Type.ALIAS;
			} else {
				if (seek(s).equals("let")) accept("let", s);
				var = next(s);
				if (seek(s).equals(":")) {
					accept(":", s);
		
					type = Type.LABEL;
				} else {
					accept("=", s);
					label = next(s);
					
					if (seek(s).equals("cconv")) {
						accept("cconv", s);
						cconv = true;
					}
					
					if (seek(s).equals("(") || cconv) {
						accept("(", s);
						if (!seek(s).equals(")")) while (true) {
							args.add(next(s));
							if (accept(new String[]{",", ")"}, s).equals(")")) break;
						} else accept(")", s);
						type = Type.ASSIGNF;
					} else type = Type.ASSIGN;
				}
			}
			
			
		}

		@Override
		public String generate(String a) {
			return null;
		}
		
	}
	
}
