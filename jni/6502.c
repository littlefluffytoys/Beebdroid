/*
 * 6502 CPU emulation (C version)
 *
 * Written by Reuben Scratton.
 *
 * Originally by Tom Walker
 *
 */

#include "main.h"


M6502 acpu;
M6502* the_cpu = &acpu;

uint8_t ram_fe30,ram_fe34;
uint8_t* roms;


uint8_t acccon;
int otherstuffcount=0;
int romsel;
int FEslowdown[8]={1,0,1,1,0,0,1,0};


#define readmem(x)  ((x<0xfe00) ? cpu->mem[x] : readmem_ex(x))
#define readword(x) ((x<0xfe00) ? (*((uint16_t*)&(cpu->mem[x]))) : (readmem_ex(x) | (readmem_ex(x+1)<<8)))
#define readwordpc() readword(cpu->pc); cpu->pc+=2
#define writemem(x, val) if (((uint16_t)x)<0x8000u) cpu->mem[x]=val; else writemem_ex(x, val)
///#define setzn(v) cpu->p_z=!(v); cpu->p_n=(v)&0x80
#define SET_FLAG(flag,val) if (val) cpu->p |= flag; else cpu->p &= ~flag
#define setzn(v) if(v) cpu->p &=~FLAG_Z; else cpu->p|=FLAG_Z;  if ((v)&0x80) cpu->p|=FLAG_N; else cpu->p&=~FLAG_N;
#define push(v) cpu->mem[0x100+(cpu->s--)]=v
#define pull()  cpu->mem[0x100+(++cpu->s)]


uint8_t readmem_ex(uint16_t addr)
{
        uint8_t temp;
        switch (addr&~3)
        {
                case 0xFE00: case 0xFE04: return readcrtc(addr);
                case 0xFE08: case 0xFE0C: /*return readacia(addr);*/break;
                //case 0xFE18: if (MASTER) return readadc(addr); break;
                case 0xFE40: case 0xFE44: case 0xFE48: case 0xFE4C:
                case 0xFE50: case 0xFE54: case 0xFE58: case 0xFE5C:
					temp=readsysvia(addr);
					return temp;
                case 0xFE60: case 0xFE64: case 0xFE68: case 0xFE6C:
                case 0xFE70: case 0xFE74: case 0xFE78: case 0xFE7C:
                	temp=readuservia(addr);
                	return temp;
                case 0xFE80: case 0xFE84: case 0xFE88: case 0xFE8C:
                case 0xFE90: case 0xFE94: case 0xFE98: case 0xFE9C:
                	//if (!MASTER) {
                        //if (WD1770) return read1770(addr);
                        return read8271(addr);
                	//}
                	break;
                case 0xFEC0: case 0xFEC4: case 0xFEC8: case 0xFECC:
                case 0xFED0: case 0xFED4: case 0xFED8: case 0xFEDC:
                	/*if (!MASTER)*/ return readadc(addr);
                	break;
                case 0xFEE0: case 0xFEE4: case 0xFEE8: case 0xFEEC:
                case 0xFEF0: case 0xFEF4: case 0xFEF8: case 0xFEFC:
                	break;
        }
        if (addr>=0xFC00 && addr<0xFE00) return 0xFF;
        //if (addr>=0xFe00 && addr<0xFf00) return addr>>8;
        //LOGI("reading from 0x%04X", addr);
        return the_cpu->mem[addr]; //
}
uint16_t readword_ex(uint16_t addr)
{
	return readmem_ex(addr) | (readmem_ex(addr+1)<<8);
}


void writemem_ex(uint16_t addr, uint8_t val)
{
	int c;
	if (addr<0xFC00 || addr>=0xFF00) return;
	//PUT BACK SOMEDAY if (addr<0xFE00 || FEslowdown[(addr>>5)&7]) { if (cycles&1) {polltime(2);} else { polltime(1); } }
	switch (addr&~3)
	{
			case 0xFE00: case 0xFE04: writecrtc(addr,val); break;
			case 0xFE08: case 0xFE0C: /*writeacia(addr,val);*/ break;
			case 0xFE10: case 0xFE14: /*writeserial(addr,val);*/ break;
			//case 0xFE18: if (MASTER) writeadc(addr,val); break;
			case 0xFE20: writeula(addr,val); break;
			//case 0xFE24: if (MASTER) write1770(addr,val); else writeula(addr,val); break;
			//case 0xFE28: if (MASTER) write1770(addr,val); break;
			case 0xFE30:
				ram_fe30=val;
				if (romsel != (val&15)) {
					romsel = val&15;
					LOGI("ROMSEL set to %d", romsel);
					memcpy(the_cpu->mem+0x8000, roms+(romsel*16384), 16384);
				}
				break;
			case 0xFE34:
				ram_fe34=val;
				break;
			case 0xFE40: case 0xFE44: case 0xFE48: case 0xFE4C:
			case 0xFE50: case 0xFE54: case 0xFE58: case 0xFE5C:
				writesysvia(addr,val);
				break;
			case 0xFE60: case 0xFE64: case 0xFE68: case 0xFE6C:
			case 0xFE70: case 0xFE74: case 0xFE78: case 0xFE7C:
				writeuservia(addr,val);
				break;
			case 0xFE80: case 0xFE84: case 0xFE88: case 0xFE8C:
			case 0xFE90: case 0xFE94: case 0xFE98: case 0xFE9C:
//                        break;
//			if (!MASTER)
			{
					/*if (WD1770) write1770(addr,val);
					else*/        write8271(addr,val);
			}
			break;
			case 0xFEC0: case 0xFEC4: case 0xFEC8: case 0xFECC:
			case 0xFED0: case 0xFED4: case 0xFED8: case 0xFEDC:
				/*if (!MASTER)*/ writeadc(addr,val);
				break;
			case 0xFEE0: case 0xFEE4: case 0xFEE8: case 0xFEEC:
			case 0xFEF0: case 0xFEF4: case 0xFEF8: case 0xFEFC:
				// writetubehost(addr,val);
				break;
	}
}


void reset6502()
{
	int c;
	the_cpu->cycles=0;
	the_cpu->pc=*(uint16_t*)&(the_cpu->mem[0xfffc]);
	the_cpu->p |= FLAG_I;
	the_cpu->nmi =0;
	//output=0;
	LOGI("\n\n6502 RESET!\n");
}



int adc_bcd(M6502* cpu, uint8_t temp) {
	//LOGI("Doing ADC BCD! %d", temp);
	register int ah=0;
	register uint8_t tempb = cpu->a+temp+((cpu->p & FLAG_C)?1:0);
	if (!tempb)
	   cpu->p |= FLAG_Z;
	register int al=(cpu->a&0xF)+(temp&0xF)+((cpu->p & FLAG_C)?1:0);
	if (al>9) {
		al-=10;
		al&=0xF;
		ah=1;
	}
	ah+=((cpu->a>>4)+(temp>>4));
	if (ah&8)
		cpu->p |= FLAG_N;
	SET_FLAG(FLAG_V, (((ah << 4) ^ cpu->a) & 128) && !((cpu->a ^ temp) & 128));
	cpu->p &= ~FLAG_C;
	if (ah>9) {
		cpu->p |= FLAG_C;
		ah-=10;
		ah&=0xF;
	}
	cpu->a=(al&0xF)|(ah<<4);
}

int sbc_bcd(M6502* cpu, uint8_t temp) {
	register int hc6=0;
	cpu->p &= ~(FLAG_Z | FLAG_N);
	if (!((cpu->a-temp)-((cpu->p & FLAG_C)?0:1)))
	   cpu->p |= FLAG_Z;
	register int al=(cpu->a&15)-(temp&15)-((cpu->p & FLAG_C)?0:1);
	if (al&16) {
		al-=6;
		al&=0xF;
		hc6=1;
	}
	register int ah=(cpu->a>>4)-(temp>>4);
	if (hc6) ah--;
	if ((cpu->a-(temp+((cpu->p & FLAG_C)?0:1)))&0x80)
	   cpu->p |= FLAG_N;
	SET_FLAG(FLAG_V, (((cpu->a-(temp+((cpu->p & FLAG_C)?0:1)))^temp)&128)&&((cpu->a^temp)&128));
	cpu->p |= FLAG_C;
	if (ah&16)  {
		cpu->p &= ~FLAG_C;
		ah -= 6;
		ah &= 0xF;
	}
	cpu->a=(al&0xF)|((ah&0xF)<<4);
}



#define SBC(temp) \
	if (!(cpu->p & FLAG_D)) { \
		register uint8_t c=(cpu->p & FLAG_C)?0:1; \
		register uint16_t tempw=cpu->a-(temp+c);    \
		register int tempv=(short)cpu->a-(short)(temp+c);            \
		SET_FLAG(FLAG_V, ((cpu->a ^ (temp+c)) & (cpu->a ^ (uint8_t)tempv)&0x80)); \
		SET_FLAG(FLAG_C, tempv>=0);\
		cpu->a=tempw&0xFF;              \
		setzn(cpu->a);                  \
	}                                  \
	else {                                  \
		register int hc6=0;                               \
		cpu->p &= ~(FLAG_Z | FLAG_N);                            \
		if (!((cpu->a-temp)-((cpu->p & FLAG_C)?0:1)))            \
		   cpu->p |= FLAG_Z;                             \
		   register int al=(cpu->a&15)-(temp&15)-((cpu->p & FLAG_C)?0:1);      \
		if (al&16) {                                   \
				al-=6;                      \
				al&=0xF;                    \
				hc6=1;                       \
		}                                   \
		register int ah=(cpu->a>>4)-(temp>>4);                \
		if (hc6) ah--;                       \
		if ((cpu->a-(temp+((cpu->p & FLAG_C)?0:1)))&0x80)        \
		   cpu->p |= FLAG_N;                             \
		SET_FLAG(FLAG_V, (((cpu->a-(temp+((cpu->p & FLAG_C)?0:1)))^temp)&128)&&((cpu->a^temp)&128)); \
		cpu->p |= FLAG_C; \
		if (ah&16)  { \
			cpu->p &= ~FLAG_C; \
			ah-=6;                      \
			ah&=0xF;                    \
		}                                   \
		cpu->a=(al&0xF)|((ah&0xF)<<4);                 \
	}



typedef int (*FN)(M6502*);
FN fns[];
/*
int callcounts[256];
void dump_callcounts() {
	int i;
	for (i=0 ; i<256 ; i++) {
		LOGI("count,%02X,%d", i, callcounts[i]);
	}
	memset(callcounts, 0, sizeof(callcounts));
}*/


int vidclockacc=0;

void do_poll(M6502* cpu, int c) {

	if (otherstuffcount<=0) {
		otherstuffcount+=128;
		logvols();
		if (motorspin) {
			motorspin--;
			if (!motorspin) fdcspindown();
		}
	}



	// Time to poll hardware?
	if (c > 0) {
		/*vidclockacc += c;
		if (vidclockacc>=128) {
			pollvideo(vidclockacc);
			vidclockacc=0;
		}*/
		pollvideo(c);
		sysvia.t1c -= c;
		if (!(sysvia.acr&0x20))  sysvia.t2c-=c;
		if (sysvia.t1c<-3  || sysvia.t2c<-3)  updatesystimers();
		uservia.t1c-=c;
		if (!(uservia.acr&0x20)) uservia.t2c-=c;
		if (uservia.t1c<-3 || uservia.t2c<-3) updateusertimers();
		otherstuffcount-=c;
		if (motoron) {
			if (fdctime) {
				fdctime-=c;
				if (fdctime<=0) fdccallback();
			}
			disctime-=c;
			if (disctime<=0) {
				disctime+=16; disc_poll();
			}
		}
	}

}

void logasm(int v) {
	LOGI("here! %08x", v);
}
void log_undef_opcode(M6502* cpu) {
	LOGI("Undefined opciode! pc=%04x", cpu->pc);
}


int fn_arr(M6502* cpu) {
	cpu->a&=readmem(cpu->pc); cpu->pc++;
	register int tempi=cpu->p & FLAG_C;
	if (cpu->p & FLAG_D) // This instruction is just as broken on a real 6502 as it is here
	{
		SET_FLAG(FLAG_V, ((cpu->a>>6)^(cpu->a>>7))); // V set if bit 6 changes in ROR
		cpu->a>>=1;
			if (tempi) cpu->a|=0x80;
			setzn(tempi);
		    cpu->p &= ~FLAG_C;
			if ((cpu->a&0xF)+(cpu->a&1)>5) cpu->a=(cpu->a&0xF0)+((cpu->a&0xF)+6); // Do broken fixup
			if ((cpu->a&0xF0)+(cpu->a&0x10)>0x50) { cpu->a+=0x60; cpu->p |= FLAG_C; }
	}
	else
	{       //V & C flag behaviours in 64doc.txt are backwards
		SET_FLAG(FLAG_V, ((cpu->a>>6)^(cpu->a>>7))); //V set if bit 6 changes in ROR
		cpu->a>>=1;
		if (tempi) cpu->a|=0x80;
		setzn(cpu->a);
		SET_FLAG(FLAG_C, cpu->a&0x40);
	}
	return 2;
}
int fn_brk(M6502* cpu) {
	//LOGI("BRK! at %04X", cpu->pc);
	cpu->pc++;
	push(cpu->pc>>8);
	push(cpu->pc&0xFF);
	register uint8_t temp=0x30 | cpu->p;
	push(temp);
	cpu->pc=*(uint16_t*)&(cpu->mem[0xfffe]);
	cpu->p |= FLAG_I;
	cpu->takeint=0;
	return -7;
}




int fn_asr_imm(M6502* cpu) {
	cpu->a &= readmem(cpu->pc); cpu->pc++;
	SET_FLAG(FLAG_C, cpu->a & 1);
	cpu->a >>= 1;
	setzn(cpu->a);
	return 2;
}



int fn_anc_imm(M6502* cpu) {
	cpu->a &= readmem(cpu->pc);
	cpu->pc++;
	setzn(cpu->a);
	SET_FLAG(FLAG_C, cpu->p & FLAG_N);
	return 2;
}


int fn_sre_x(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc)+cpu->x; cpu->pc++;
	register uint16_t addr=readword(temp);
	temp=readmem(addr);
	SET_FLAG(FLAG_C, temp&1);
	temp>>=1;
	writemem(addr,temp);
	cpu->a ^= temp;
	setzn(cpu->a);
	return 7;
}


int fn_sre_zp(M6502* cpu) {
	register uint16_t addr=readmem(cpu->pc); cpu->pc++;
	register uint8_t temp=readmem(addr);
	SET_FLAG(FLAG_C, temp&1);
	temp>>=1;
	writemem(addr,temp);
	cpu->a ^= temp;
	setzn(cpu->a);
	return 5;
}

int fn_sre_abs(M6502* cpu) {
	register uint16_t addr=readwordpc();
	register uint8_t temp=readmem(addr);
	SET_FLAG(FLAG_C, temp&1);
	temp>>=1;
	writemem(addr,temp);
	cpu->a^=temp;
	setzn(cpu->a);
	return 6;
}

int fn_sre_y(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	register uint16_t addr=readword(temp);
	register int clocks = 7;
	if ((addr&0xFF00)^((addr+cpu->y)&0xFF00))clocks++;
	temp=readmem(addr+cpu->y);
	//?writemem(addr+cpu->y,temp);
	SET_FLAG(FLAG_C, temp&1);
	temp>>=1;
	writemem(addr+cpu->y,temp);
	cpu->a^=temp;
	setzn(cpu->a);
	return clocks;
}


int fn_sre_zp_x(M6502* cpu) {
	register uint16_t addr=(readmem(cpu->pc)+cpu->x)&0xFF; cpu->pc++;
	register uint8_t temp=readmem(addr);
	//writemem(addr,temp);
    SET_FLAG(FLAG_C, temp&1);
	temp>>=1;
	writemem(addr,temp);
	cpu->a^=temp;
	setzn(cpu->a);
	return 5;
}


int fn_sre_abs_y(M6502* cpu) {
	register uint16_t addr=readwordpc();
	register int clocks = 6;
	if ((addr&0xFF00)^((addr+cpu->y)&0xFF00)) clocks++;
	register uint8_t temp=readmem(addr+cpu->y);
	writemem(addr+cpu->y,temp);
    SET_FLAG(FLAG_C, temp&1);
	temp>>=1;
	writemem(addr+cpu->y,temp);
	cpu->a^=temp;
	setzn(cpu->a);
	return clocks;
}


int fn_sre_abs_x(M6502* cpu) {
	register uint16_t addr=readwordpc();
	register int clocks = 6;
	if ((addr&0xFF00)^((addr+cpu->x)&0xFF00)) clocks++;
	register uint8_t temp=readmem(addr+cpu->x);
	writemem(addr+cpu->x,temp);
    SET_FLAG(FLAG_C, temp&1);
	temp>>=1;
	writemem(addr+cpu->x,temp);
	cpu->a^=temp;
	setzn(cpu->a);
	return clocks;
}


int fn_sax_x(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc)+cpu->x; cpu->pc++;
	register uint16_t addr=readword(temp);
	writemem(addr,cpu->a&cpu->x);
	return 6;
}

int fn_sax_zp(M6502* cpu) {
	register uint16_t addr=readmem(cpu->pc); cpu->pc++;
	writemem(addr,cpu->a&cpu->x);
	return 3;
}

int fn_ane(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	cpu->a=(cpu->a|0xEE)&cpu->x&temp; //Internal parameter always 0xEE on BBC, always 0xFF on Electron
	setzn(cpu->a);
	return 2;
}

int fn_sax_abs(M6502* cpu) {
	register uint16_t addr=readwordpc();
	writemem(addr,cpu->a&cpu->x);
	return 4;
}


int fn_sha_y(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	register uint16_t addr=readword(temp);
	writemem(addr+cpu->y,cpu->a&cpu->x&((addr>>8)+1));
	return 6;
}

int fn_sax_zp_y(M6502* cpu) {
	register uint16_t addr=readmem(cpu->pc); cpu->pc++;
	writemem((addr+cpu->y)&0xFF,cpu->a&cpu->x);
	return 4;
}

int fn_shs_abs_y(M6502* cpu) {
	register uint16_t addr=readwordpc();
	readmem((addr&0xFF00)+((addr+cpu->y)&0xFF));
	writemem(addr+cpu->y,cpu->a&cpu->x&((addr>>8)+1));
	cpu->s=cpu->a&cpu->x;
	return 5;
}

int fn_shy_abs_x(M6502* cpu) {
	register uint16_t addr=readwordpc();
	readmem((addr&0xFF00)+((addr+cpu->x)&0xFF));
	writemem(addr+cpu->x,cpu->y&((addr>>8)+1));
	return 5;
}

int fn_shx_abs_y(M6502* cpu) {
	register uint16_t addr=readwordpc();
	readmem((addr&0xFF00)|((addr+cpu->x)&0xFF));
	writemem(addr+cpu->y,cpu->x&((addr>>8)+1));
	return 5;
}

int fn_sha_abs_y(M6502* cpu) {
	register uint16_t addr=readwordpc();
	readmem((addr&0xFF00)|((addr+cpu->x)&0xFF));
	writemem(addr+cpu->y, cpu->a & cpu->x & ((addr>>8)+1));
	return 5;
}


int fn_lax_y(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc)+cpu->x; cpu->pc++;
	register uint16_t addr=readword(temp);
	cpu->a=cpu->x=readmem(addr);
	setzn(cpu->a);
	return 6;
}

int fn_lax_zp(M6502* cpu) {
	register uint16_t addr=readmem(cpu->pc); cpu->pc++;
	cpu->a=cpu->x=readmem(addr);
	setzn(cpu->a);
	return 3;
}
int fn_lax(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	cpu->a=cpu->x=((cpu->a|0xEE)&temp); //WAAAAY more complicated than this, but it varies from machine to machine anyway
	setzn(cpu->a);
	return 2;
}
int fn_lax_abs(M6502* cpu) {
	register uint16_t addr=readwordpc();
	cpu->a=cpu->x=readmem(addr);
	setzn(cpu->a);
	return 4;
}

int fn_lax_y2(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	register uint16_t addr=readword(temp);
	register int clocks = 5;
	if ((addr&0xFF00)^((addr+cpu->y)&0xFF00)) clocks++;
	cpu->a=cpu->x=readmem(addr+cpu->y);
	setzn(cpu->a);
	return clocks;
}
int fn_lax_zp_y(M6502* cpu) {
	register uint16_t addr=readmem(cpu->pc); cpu->pc++;
    cpu->a=cpu->x=readmem((addr+cpu->y)&0xFF);
	setzn(cpu->a);
	return 3;
}

int fn_las_abs_y(M6502* cpu) {
	register uint16_t addr=readwordpc();
	int cl = 4;
	if ((addr&0xFF00)^((addr+cpu->y)&0xFF00)) cl++;
    cpu->a= cpu->x = cpu->s = cpu->s & readmem(addr+cpu->y); //No, really!
	setzn(cpu->a);
	return cl;
}


int fn_lax_abs_y(M6502* cpu) {
	register uint16_t addr=readwordpc();
	int cl=4;
	if ((addr&0xFF00)^((addr+cpu->y)&0xFF00)) cl++;
    cpu->a=cpu->x=readmem(addr+cpu->y);
	setzn(cpu->a);
	return cl;
}
int fn_dcp_indx(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc)+cpu->x; cpu->pc++;
	register uint16_t addr=readword(temp);
	temp=readmem(addr);
	writemem(addr,temp);
	temp--;
	writemem(addr,temp);
	setzn(cpu->a-temp);
    SET_FLAG(FLAG_C, cpu->a>=temp);
	return 7;
}

int fn_dcp_zp(M6502* cpu) {
	register uint16_t addr=readmem(cpu->pc); cpu->pc++;
	register uint8_t temp=readmem(addr)-1;
	writemem(addr,temp);
	setzn(cpu->a-temp);
    SET_FLAG(FLAG_C, cpu->a>=temp);
	return 5;
}

int fn_sbx_imm(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	setzn((cpu->a&cpu->x)-temp);
    SET_FLAG(FLAG_C, (cpu->a&cpu->x)>=temp);
    cpu->x=(cpu->a&cpu->x)-temp;
	return 2;
}
int fn_dcp_abs(M6502* cpu) {
	register uint16_t addr=readwordpc();
	register uint8_t temp=readmem(addr)-1;
	writemem(addr,temp);
	setzn(cpu->a-temp);
    SET_FLAG(FLAG_C, cpu->a>=temp);
	return 6;
}

int fn_dcp_indy(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	register uint16_t addr=readword(temp);
	int clocks = 7;
	if ((addr&0xFF00)^((addr+cpu->y)&0xFF00)) clocks++;
	temp=readmem(addr)-1;
	writemem(addr,temp+1);
	writemem(addr,temp);
	setzn(cpu->a-temp);
    SET_FLAG(FLAG_C, cpu->a>=temp);
	return clocks;
}

int fn_dcp_zp_x(M6502* cpu) {
	register uint16_t addr=(readmem(cpu->pc)+cpu->x)&0xFF; cpu->pc++;
	register uint8_t temp=readmem(addr)-1;
	writemem(addr,temp);
	setzn(cpu->a-temp);
    SET_FLAG(FLAG_C, cpu->a>=temp);
	return 5;
}

int fn_dcp_abs_y(M6502* cpu) {
	register uint16_t addr=readwordpc();
	int clocks = 6;
	if ((addr&0xFF00)^((addr+cpu->y)&0xFF00)) clocks++;
	register uint8_t temp=readmem(addr+cpu->y)-1;
	//writemem(addr+cpu->y,temp+1);
	writemem(addr+cpu->y,temp);
	setzn(cpu->a-temp);
    SET_FLAG(FLAG_C, cpu->a>=temp);
	return clocks;
}

int fn_dcp_abs_x(M6502* cpu) {
	register uint16_t addr=readwordpc();
	int clocks = 6;
	if ((addr&0xFF00)^((addr+cpu->x)&0xFF00)) clocks++;
	register uint8_t temp=readmem(addr+cpu->x)-1;
	//writemem(addr+cpu->x,temp+1);
	writemem(addr+cpu->x,temp);
	setzn(cpu->a-temp);
    SET_FLAG(FLAG_C, cpu->a>=temp);
	return clocks;
}

int fn_isb_indx(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc)+cpu->x; cpu->pc++;
	register uint16_t addr=readword(temp);
	temp=readmem(addr);
	temp++;
	writemem(addr,temp);
	SBC(temp);
	return 7;
}

int fn_isb_zp(M6502* cpu) {
	register uint16_t addr=readmem(cpu->pc); cpu->pc++;
	register uint8_t temp=readmem(addr);
	//?writemem(addr,temp);
	temp++;
	writemem(addr,temp);
	SBC(temp);
	return 5;
}

int fn_isb_abs(M6502* cpu) {
	register uint16_t addr=readwordpc();
	register uint8_t temp=readmem(addr);
	temp++;
	writemem(addr,temp);
	SBC(temp);
	return 6;
}


int fn_isb_indy(M6502* cpu) {
	register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	register uint16_t addr=readword(temp);
	int clocks = 7;
	if ((addr&0xFF00)^((addr+cpu->y)&0xFF00)) clocks++;
	temp=readmem(addr+cpu->y);
	temp++;
	writemem(addr+cpu->y,temp);
	SBC(temp);
	return clocks;
}


int fn_isb_zp_x(M6502* cpu) {
	register uint16_t addr=(readmem(cpu->pc)+cpu->x)&0xFF; cpu->pc++;
	register uint8_t temp=readmem(addr);
	temp++;
	writemem(addr,temp);
	SBC(temp);
	return 5;
}


int fn_isb_abs_y(M6502* cpu) {
	register uint16_t addr=readwordpc();
	addr += cpu->y;
	register uint8_t temp=readmem(addr);
	temp++;
	writemem(addr,temp);
	SBC(temp);
	return 7;
}


int fn_isb_abs_x(M6502* cpu) {
	register uint16_t addr=readwordpc();
	addr += cpu->x;
	register uint8_t temp=readmem(addr);
	temp++;
	writemem(addr,temp);
	SBC(temp);
	return 7;
}

FN fns[] = {
	fn_brk, 	// 0x00
	0, 	// 0x01  	ORA (,x)
	0,
	0,  // 0x03: Undocumented - SLO (,x)
	0,  // 0x04: Undocumented - NOP zp
	0,	// 0x05: ORA zp
	0,  // 0x06: ASL zp
	0,  // 0x07: Undocumented - SLO zp
	0,     // 0x08: PHP
	0, // 0x09: ORA imm
	0,   // 0x0A: ASL A
	fn_anc_imm, // 0x0B: Undocumented - ANC imm
	0, // 0x0C: Undocumented - NOP abs
	0, // 0x0D: ORA abs
	0, // 0x0E: ASL abs
	0, // 0x0F: Undocumented - SLO abs
	0,     // 0x10: BPL
	0,   // 0x11: ORA (),y
	0,
	0,   // 0x13: Undocumented - SLO (),y
	0,// 0x14: Undocumented - NOP zp,x
	0,// 0x15: ORA zp,x
	0,// 0x16: ASL zp,x
	0,// 0x17: Undocumented - SLO zp,x
	0,     // 0x18: CLC
	0,//0x19: ORA abs,y
	0,     // 0x1A: Undocumented - NOP
	0,//0x1B: Undocumented - SLO abs,y
	0,//0x1C: Undocumented - NOP abs,x
	0,//0x1D: ORA abs,x
	0,//0x1E: ASL abs,x
	0,//0x1F: Undocumented - SLO abs,x
	0,     // 0x20: JSR
	0,   // 0x21: AND (,x)
	0,
	0,	// 0x23: Undocumented - RLA (,x)
	0,	// 0x24: BIT zp
	0,  // 0x25: AND zp
	0,  // 0x26: ROL zp
	0,  // 0x27: Undocumented - RLA zp
	0,     // 0x28: PLP
	0,		// 0x29: AND
	0,   // 0x2A: ROL A
	fn_anc_imm, // 0x2B: Undocumented - ANC imm
	0, // 0x2C: BIT abs
	0, // 0x2D: AND abs
	0, // 0x2E: ROL abs
	0, // 0x2F: Undocumented - RLA abs
	0, 	// 0x30: BMI
	0,   // 0x31: AND (),y
	0,
	0,   // 0x33: Undocumented - RLA (),y
	0,// 0x34: Undocumented - NOP zp,x
	0,// 0x35: AND zp,x
	0,// 0x36: ROL zp,x
	0,// 0x37: Undocumented - RLA zp,x
	0, 	// 0x38: SEC
	0,//0x39: AND abs,y
	0,	    // 0x3A: Undocumented - NOP
	0,//0x3B: Undocumented - RLA abs,y
	0,//0x3C: Undocumented - NOP abs,x
	0,//0x3D: AND abs,x
	0,//0x3E: ROL abs,x
	0,//0x3F: Undocumented - RLA abs,x
	0,     // 0x40: RTI
	0,   // 0x41: EOR (,x)
	0,
	fn_sre_x,   // 0x43: Undocumented - SRE (,x)
	0,  // 0x44:
	0,  // 0x45: EOR zp
	0,  // 0x46: LSR zp
	fn_sre_zp,  // 0x47: Undocumented - SRE zp
	0,     // 0x48: PHA
	0, // 0x49: EOR imm
	0,   // 0x4A: LSR A
	fn_asr_imm, // 0x4B: Undocumented - ASR imm
	0,		// 0x4C: JMP
	0, // 0x4D: EOR abs
	0, // 0x4E: LSR abs
	fn_sre_abs, // 0x4F: Undocumented - SRE abs
	0,     // 0x50: BVC
	0,   // 0x51: EOR (),y
	0,
	fn_sre_y,   // 0x53: Undocumented - SRE (),y
	0,// 0x54:
	0,// 0x55: EOR zp,x
	0,// 0x56: LSR zp,x
	0,// 0x57: Undocumented - SRE zp,x
	0,     // 0x58: CLI
	0,//0x59: EOR abs,y
	0,      //0x5A: Undocumented - NOP
	fn_sre_abs_y,//0x5B: Undocumented - SRE abs,y
	0,//0x5C: Undocumented - NOP abs,x
	0,//0x5D: EOR abs,x
	0,//0x5E: LSR abs,x
	fn_sre_abs_x,//0x5F: Undocumented - SRE abs,x
	0, 	// 0x60: RTS
	0,   // 0x61: ADC (,x)
	0,
	0,   // 0x63: Undocumented - RRA (,x)
	0,  // 0x64: Undocumented - NOP zp
	0,  // 0x65: ADC zp
	0,  // 0x66: ROR zp
	0,  // 0x67: Undocumented - RRA zp
	0,     // 0x68: PLA
	0, // 0x69: ADC imm
	0,   // 0x6A: ROR A
	fn_arr,     // 0x6B: Undocumented - ARR
	0, // 0x6C: JMP ()
	0, // 0x6D: ADC abs
	0, // 0x6E: ROR abs
	0, // 0x6F: Undocumented - RRA abs
	0,     // 0x70: BVS
	0,   // 0x71: ADC (),y
	0,
	0,   // 0x73: Undocumented - RRA (,y)
	0,// 0x74: Undocumented - NOP zp,x
	0,// 0x75: ADC zp,x
	0,// 0x76: ROR zp,x
	0,// 0x77: Undocumented - RRA zp,x
	0,     // 0x78: SEI
	0,//0x79: ADC abs,y
	0,     // 0x7A: Undocumented - NOP
	0,//0x7B: Undocumented - RRA abs,y
	0,
	0,//0x7D: ADC abs,x
	0,//0x7E: ROR abs,x
	0,//0x7F: Undocumented - RRA abs,x
	0,  //0x80: Undocumented - NOP imm
	0,   // 0x81: STA (,x)
	0, // 0x82: Undocumented - NOP imm
	fn_sax_x,   // 0x83: Undocumented - SAX (,x)
	0,  // 0x84: STY zp
	0,  // 0x85: STA zp
	0,  // 0x86: STX zp
	fn_sax_zp,  // 0x87: Undocumented - SAX zp
	0,     // 0x88: DEY
	0, // 0x89: Undocumented - NOP imm
	0,     // 0x8A: TXA
	fn_ane,     // 0x8B: Undocumented - ANE
	0, // 0x8C: STY abs
	0, // 0x8D: STA abs
	0, // 0x8E: STX abs
	fn_sax_abs, // 0x8F: Undocumented - SAX abs
	0,     // 0x90: BCC
	0,   // 0x91: STA (),y
	0,
	fn_sha_y,   // 0x93: Undocumented - SHA (),y
	0,// 0x94: STY zp,x
	0,// 0x95: STA zp,x
	0,// 0x96: STX zp,y
	fn_sax_zp_y,// 0x97: Undocumented - SAX zp,y
	0,     // 0x98: TYA
	0,//0x99: STA abs,y
	0,     // 0x9A: TXS
	fn_shs_abs_y,//0x9B: Undocumented - SHS abs,y
	fn_shy_abs_x,//0x9C: Undocumented - SHY abs,x
	0,//0x9D: STA abs,x
	fn_shx_abs_y,//0x9E: Undocumented - SHX abs,y
	fn_sha_abs_y,//0x9F: Undocumented - SHA abs,y
	0, // 0xA0: LDY imm
	0,   // 0xA1: LDA (,x)
	0, // 0xA2: LDX imm
	fn_lax_y,   // 0xA3: Undocumented - LAX (,y)
	0,  // 0xA4: LDY zp
	0,  // 0xA5: LDA zp
	0,  // 0xA6: LDX zp
	fn_lax_zp,  // 0xA7: Undocumented - LAX zp
	0,     // 0xA8: TAY
	0, // 0xA9: LDA imm
	0,     // 0xAA: TAX
	fn_lax,     // 0xAB: Undocumented - LAX
	0, // 0xAC: LDY abs
	0, // 0xAD: LDA abs
	0, // 0xAE: LDX abs
	0, // 0xAF: LAX abs
	0,     // 0xB0: BCS
	0,   // 0xB1: LDA (),y
	0,
	fn_lax_y2,  // 0xB3: LAX (),y
	0,// 0xB4: LDY zp,x
	0,// 0xB5: LDA zp,x
	0,// 0xB6: LDX zp,y
	0,// 0xB7: LAX zp,y
	0,     // 0xB8: CLV
	0,//0xB9: LDA abs,y
	0,     // 0xBA: TSX
	fn_las_abs_y,//0xBB: Undocumented - LAS abs,y
	0,//0xBC: LDY abs,x
	0,//0xBD: LDA abs,x
	0,//0xBE: LDX abs,y
	0,//0xBF: LAX abs,y
	0, // 0xC0: CPY imm
	0,// 0xC1: CMP (,x)
	0, // 0xC2: Undocumented - NOP imm
	fn_dcp_indx,// 0xC3: Undocumented - DCP (,x)
	0,  // 0xC4: CPY zp
	0,  // 0xC5: CMP zp
	0,  // 0xC6: DEC zp
	fn_dcp_zp,  // 0xC7: Undocumented - DCP zp
	0,     // 0xC8: INY
	0, // 0xC9: CMP imm
	0,     // 0xCA: DEX
	fn_sbx_imm, // 0xCB: Undocumented - SBX imm
	0, // 0xCC: CPY abs
	0, // 0xCD: CMP abs
	0, // 0xCE: DEC abs
	fn_dcp_abs, // 0xCF: Undocumented - DCP abs
	0,     // 0xD0: BNE
	0,// 0xD1: CMP (),y
	0,
	fn_dcp_indy,// 0xD3: Undocumented - DCP (),y
	0,// 0xD4: Undocumented - NOP zp,x
	0,// 0xD5: CMP zp,x
	0,// 0xD6: DEC zp,x
	fn_dcp_zp_x,// 0xD7: Undocumented - DCP zp,x
	0,     // 0xD8: CLD
	0,//0xD9: CMP abs,y
	0,     // 0xDA: Undocumented - NOP
	fn_dcp_abs_y,//0xDB: Undocumented - DCP abs,y
	0,//0xDC: Undocumented - NOP abs,x
	0,//0xDD: CMP abs,x
	0,//0xDE: DEC abs,x
	fn_dcp_abs_x,//0xDF: Undocumented - DCP abs,x
	0, // 0xE0: CPX imm
	0,// 0xE1: SBC (,x)
	0, // 0xE2: Undocumented - NOP imm
	fn_isb_indx,// 0xE3: Undocumented - ISB (,x)
	0,  // 0xE4: CPX zp
	0,  // 0xE5: SBC zp
	0,  // 0xE6: INC zp
	fn_isb_zp,  // 0xE7: Undocumented - ISB zp
	0,     // 0xE8: INX
	0, // 0xE9: SBC imm
	0,     // 0xEA: NOP
	0, // 0xEB: Undocumented - SBC imm
	0, // 0xEC: CPX abs
	0, // 0xED: SBC abs
	0, // 0xEE: INC abs
	fn_isb_abs, // 0xEF: Undocumented - ISB abs
	0,     // 0xF0: BEQ
	0,// 0xF1: SBC (),y
	0,
	fn_isb_indy,// 0xF3: Undocumented - ISB (),y
	0, // 0xF4: Undocumented - NOP zpx
	0,// 0xF5: SBC zp,x
	0,// 0xF6: INC zp,x
	fn_isb_zp_x,// 0xF7: Undocumented - ISB zp,x
	0,     // 0xF8: SED
	0,//0xF9: SBC abs,y
	0,     // 0xFA: Undocumented - NOP
	fn_isb_abs_y,//0xFB: Undocumented - ISB abs,y
	0,//0xFC: Undocumented - NOP abs,x
	0,//0xFD: SBC abs,x
	0,//0xFE: INC abs,x
	fn_isb_abs_x,//0xFF: Undocumented - ISB abs,x
};
//*/

uint8_t* save_cpu(uint8_t* p)
{
	*p++ = the_cpu->a;
	*p++ = the_cpu->x;
	*p++ = the_cpu->y;
	*p++ = the_cpu->p;
	*p++ = the_cpu->s;
	*(uint16_t*)p = the_cpu->pc; p+=2;
	*p++ = the_cpu->nmi;
	*p++ = the_cpu->interrupt;
	*(uint32_t*)p = the_cpu->cycles; p+=4;
	return p;
}

uint8_t* load_cpu(uint8_t* p)
{
	the_cpu->a=*p++;
	the_cpu->x=*p++;
	the_cpu->y=*p++;
	the_cpu->p=*p++;
	the_cpu->s=*p++;
	the_cpu->pc = *(uint16_t*)p; p+=2;
	the_cpu->nmi=*p++;
	the_cpu->interrupt=*p++;
	the_cpu->cycles = *(uint32_t*)p; p+=4;
	return p;
}

