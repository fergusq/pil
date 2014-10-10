package org.kaivos.proceed.compiler;

public class Registers {

	public static int REGISTER_SIZE_BIT = 32;
	public static int REGISTER_SIZE = REGISTER_SIZE_BIT/8;
	public static int REGISTER_SIZE_W = REGISTER_SIZE/2;
	
	public static String PREFIX = "e";
	
	public static String
		WORD = "word",
		DWORD = "dword",
		QWORD = "qword";
	
	public static String SIZE = DWORD;
	
	public static String[] amd64Arguments = {"rdi", "rsi", "rdx", "rcx", "r8", "r9"};

	public static String ARCH = "x86";
	
	private static boolean guard = true;
	
	static {
		if (System.getProperty("os.arch").equals("amd64") && guard) {
			toAmd64();
		}
	}
	
	public static String AX = PREFIX + "ax",
			BX = PREFIX + "bx",
			CX = PREFIX + "cx",
			DX = PREFIX + "dx",
			
			BP = PREFIX + "bp",
			SP = PREFIX + "sp",
			
			SI = PREFIX + "si",
			DI = PREFIX + "di";
	
	public static void toX86() {
		guard = false;
		REGISTER_SIZE_BIT = 32;
		REGISTER_SIZE = REGISTER_SIZE_BIT/8;
		REGISTER_SIZE_W = REGISTER_SIZE/2;
		PREFIX = "e";
		SIZE = DWORD;
		ARCH = "x86";
		
		AX = PREFIX + "ax";
		BX = PREFIX + "bx";
		CX = PREFIX + "cx";
		DX = PREFIX + "dx";
				
		BP = PREFIX + "bp";
		SP = PREFIX + "sp";
				
		SI = PREFIX + "si";
		DI = PREFIX + "di";
	}
	
	public static void toAmd64() {
		guard = false;
		REGISTER_SIZE_BIT = 64;
		REGISTER_SIZE = REGISTER_SIZE_BIT/8;
		REGISTER_SIZE_W = REGISTER_SIZE/2;
		PREFIX = "r";
		SIZE = QWORD;
		ARCH = "amd64";
		
		AX = PREFIX + "ax";
		BX = PREFIX + "bx";
		CX = PREFIX + "cx";
		DX = PREFIX + "dx";
				
		BP = PREFIX + "bp";
		SP = PREFIX + "sp";
				
		SI = PREFIX + "si";
		DI = PREFIX + "di";
	}
	
}
