/*B-em v2.1 by Tom Walker
  System VIA + keyboard emulation*/

#include "main.h"

VIA sysvia;
void updatekeyboard();

#define TIMER1INT 0x40
#define TIMER2INT 0x20
#define PORTBINT  0x18
#define PORTAINT  0x03

#define		ORB     0x00
#define		ORA	0x01
#define		DDRB	0x02
#define		DDRA	0x03
#define		T1CL	0x04
#define		T1CH	0x05
#define		T1LL	0x06
#define		T1LH	0x07
#define		T2CL	0x08
#define		T2CH	0x09
#define		SR	0x0a
#define		ACR	0x0b
#define		PCR	0x0c
#define		IFR	0x0d
#define		IER	0x0e
#define		ORAnh   0x0f


uint8_t IC32=0;
int scrsize;
int keycol,keyrow;
uint8_t keys[16][16];
uint8_t bbckey[16][16];
uint8_t sdbval;
int lns;
int vc,sc;

void updatesysIFR()
{
	if ((sysvia.ifr&0x7F)&(sysvia.ier&0x7F)) {
		sysvia.ifr|=0x80;
		the_cpu->interrupt|=1;
	}
	else {
		sysvia.ifr&=~0x80;
		the_cpu->interrupt&=~1;
	}
}

void updatesystimers()
{
	if (sysvia.t1c<-3) {
		while (sysvia.t1c<-3)
			  sysvia.t1c+=sysvia.t1l+4;
		if (!sysvia.t1hit) {
			sysvia.ifr|=TIMER1INT;
			updatesysIFR();
		}
		if (!(sysvia.acr&0x40))
		   sysvia.t1hit=1;
	}
        if (!(sysvia.acr&0x20))
        {
                if (sysvia.t2c<-3)
                {
                        if (!sysvia.t2hit)
                        {
//                                rpclog("Sys timer 2 INT %i,%i\n",vc,sc);
                                sysvia.ifr|=TIMER2INT;
                                updatesysIFR();
                        }
                        sysvia.t2hit=1;
                }
        }
}


void vblankint()
{
        if (!sysvia.ca1 && (sysvia.pcr&1)) {
                sysvia.ifr|=2;
                updatesysIFR();
        }
        sysvia.ca1=1;
}
void vblankintlow()
{
        if (sysvia.ca1 && !(sysvia.pcr&1)) {
                sysvia.ifr|=2;
                updatesysIFR();
        }
        sysvia.ca1=0;
}

void sysca2high()
{
        if ((!sysvia.ca2 && (sysvia.pcr&4))) // || OS01) /*OS 0.10 sets PCR to 0 and expects the keyboard to still work*/
        {
                sysvia.ifr|=1;
                updatesysIFR();
        }
        sysvia.ca2=1;
}
void sysca2low()
{
        if (sysvia.ca2 && !(sysvia.pcr&4))
        {
                sysvia.ifr|=1;
                updatesysIFR();
        }
        sysvia.ca2=0;
}

void syscb1()
{
        sysvia.ifr|=0x10;
        updatesysIFR();
}



void writeIC32(uint8_t val)
{
        uint8_t oldIC32=IC32;
        int temp=0;
        if (val&8)
           IC32|=(1<<(val&7));
        else
           IC32&=~(1<<(val&7));
//        printf("IC32 now %02X\n",IC32);
        scrsize=((IC32&16)?2:0)|((IC32&32)?1:0);

        // If a bit flipped
        if (!(IC32&8)&&(oldIC32&8))
        {
                keyrow=(sdbval>>4)&7;
                keycol=sdbval&0xF;
                updatekeyboard();
        }
        if (!(IC32&1) && (oldIC32&1))
           writesound(sdbval);
        /*if ((IC32&192)!=(oldIC32&192))
        {
                if (!(IC32&64)) temp|=KB_CAPSLOCK_FLAG;
                if (!(IC32&128)) temp|=KB_SCROLOCK_FLAG;
        }*/
        //rjh if (MASTER && !compactcmos) cmosupdate(IC32,sdbval);
}

void writedatabus(uint8_t val)
{
        sdbval=val;
        if (!(IC32&8))
        {
                keyrow=(val>>4)&7;
                keycol=val&0xF;
                updatekeyboard();
        }
        //rjh if (MASTER && !compactcmos) cmosupdate(IC32,sdbval);
//        if (!(IC32&1)) writesound(val);
}

void writesysvia(uint16_t addr, uint8_t val)
{
//        rpclog("Write SYS VIA %04X %02X %04X %i,%i\n",addr,val,pc,vc,sc);
        switch (addr&0xF)
        {
                case ORA:
                sysvia.ifr&=0xfd;
                if (!(sysvia.pcr&4) || (sysvia.pcr&8)) sysvia.ifr&=~1;
                updatesysIFR();
                case ORAnh:
                sysvia.ora=val;
                sysvia.porta=(sysvia.porta & ~sysvia.ddra)|(sysvia.ora & sysvia.ddra);
                writedatabus(val);
                break;

                case ORB:
                sysvia.orb=val;
                // rjh if (compactcmos)
                // rjh {
                	//rjh                         cmosi2cchange(val&0x20,val&0x10);
                // rjh }
                sysvia.portb=(sysvia.portb & ~sysvia.ddrb)|(sysvia.orb & sysvia.ddrb);
                sysvia.ifr&=0xef;//~PORTBINT;
                if (!(sysvia.pcr&0x40) || (sysvia.pcr&0x80)) sysvia.ifr&=~8;
                writeIC32(val);
                updatesysIFR();
                //rjh if (MASTER && !compactcmos) cmoswriteaddr(val);
                break;

                case DDRA:
                sysvia.ddra=val;
                break;
                case DDRB:
                sysvia.ddrb=val;
                break;
                case SR:
                sysvia.sr=val;
                break;
                case ACR:
                sysvia.acr=val;
//                printf("SYS ACR now %02X\n",val);
                break;
                case PCR:
                if ((sysvia.pcr&0xE0)==0xC0 && (val&0xE0)==0xE0)
                {
                        latchpen();
                }
                sysvia.pcr=val;
//                printf("PCR write %02X %04X\n",val,pc);
                break;
                case T1LL:
//                        printf("SYS T1LL %02X\n",val);
                case T1CL:
                sysvia.t1l&=0x1FE00;
                sysvia.t1l|=(val<<1);
//                rpclog("ST1L now %04X\n",sysvia.t1l);
                break;
                case T1LH:
//                        printf("SYS T1LH %02X\n",val);
                sysvia.t1l&=0x1FE;
                sysvia.t1l|=(val<<9);
                if (sysvia.acr&0x40)
                {
                        sysvia.ifr&=~TIMER1INT;
                        updatesysIFR();
                }
//                rpclog("ST1L now %04X\n",sysvia.t1l);
                break;
                case T1CH:
                sysvia.t1l&=0x1FE;
                sysvia.t1l|=(val<<9);
                sysvia.t1c=sysvia.t1l+1;
                sysvia.ifr&=~TIMER1INT;
                updatesysIFR();
                sysvia.t1hit=0;
//                rpclog("ST1L now %04X\n",sysvia.t1l);
                break;
                case T2CL:
                sysvia.t2l&=0x1FE00;
                sysvia.t2l|=(val<<1);
//                rpclog("ST2L now %04X\n",sysvia.t2l);
                break;
                case T2CH:
                if (sysvia.t2c==-3 && (sysvia.ier&TIMER2INT) && !(sysvia.ifr&TIMER2INT))
                {
                	the_cpu->interrupt|=128;
                }
                sysvia.t2l&=0x1FE;
                sysvia.t2l|=(val<<9);
                sysvia.t2c=sysvia.t2l+1;
                sysvia.ifr&=~TIMER2INT;
                updatesysIFR();
                sysvia.t2hit=0;
//                rpclog("ST2L now %04X\n",sysvia.t2l);
                break;
                case IER:
                if (val&0x80)
                   sysvia.ier|=(val&0x7F);
                else
                   sysvia.ier&=~(val&0x7F);
                updatesysIFR();
//                printf("SYS IER now %02X\n",sysvia.ier);
                break;
                case IFR:
//                        printf("Write IFR %02X %04X\n",val,pc);
                sysvia.ifr&=~(val&0x7F);
                updatesysIFR();
                break;
        }
}

uint8_t readsysvia(uint16_t addr)
{
        uint8_t temp;
//        rpclog("Read SYS VIA %04X\n",addr);
        addr&=0xF;
        switch (addr&0xF)
        {
                case ORA:
                sysvia.ifr&=~PORTAINT;
                updatesysIFR();
                case ORAnh:
                	//rjh                 if (MASTER && cmosenabled() && !compactcmos) return cmosread();
                temp=sysvia.ora & sysvia.ddra;
                temp|=(sysvia.porta & ~sysvia.ddra);
                temp&=0x7F;
                if (bbckey[keycol][keyrow]) return temp|0x80;
                return temp;

                case ORB:
                sysvia.ifr&=0xEF;//~PORTBINT;
                updatesysIFR();
                temp=sysvia.orb & sysvia.ddrb;
                // rjh if (compactcmos)
                // rjh {
                // rjh         sysvia.irb&=~0x30;
                        //rjh if (i2cclock) sysvia.irb|=0x20;
                        //rjh if (i2cdata)  sysvia.irb|=0x10;
                // rjh }
                // rjh else
                // rjh {
                        sysvia.irb|=0xF0;
                        if (joybutton[0]) sysvia.irb&=~0x10;
                        if (joybutton[1]) sysvia.irb&=~0x20;
                        // rjh }
//                if (sysvia.acr&2)
                   temp|=(sysvia.irb & ~sysvia.ddrb);
//                else
//                   temp|=(sysvia.portb & ~sysvia.ddrb);
                return temp;

                case DDRA:
                return sysvia.ddra;
                case DDRB:
                return sysvia.ddrb;
                case T1LL:
                return (sysvia.t1l&0x1FE)>>1;
                case T1LH:
                return sysvia.t1l>>9;
                case T1CL:
                sysvia.ifr&=~TIMER1INT;
                updatesysIFR();
                if (sysvia.t1c<-1) return 0xFF;
                return ((sysvia.t1c+1)>>1)&0xFF;
                case T1CH:
                if (sysvia.t1c<-1) return 0xFF;
                return ((sysvia.t1c+1)>>1)>>8;
                case T2CL:
                sysvia.ifr&=~TIMER2INT;
                updatesysIFR();
                if (sysvia.acr&0x20) return (sysvia.t2c>>1)&0xFF;
                return ((sysvia.t2c+1)>>1)&0xFF;
                case T2CH:
                if (sysvia.acr&0x20) return (sysvia.t2c>>1)>>8;
                return ((sysvia.t2c+1)>>1)>>8;
                case SR:
                return sysvia.sr;
                case ACR:
                return sysvia.acr;
                case PCR:
                return sysvia.pcr;
                case IER:
                return sysvia.ier|0x80;
                case IFR:
                return sysvia.ifr;
        }
        return 0xFE;
}

void resetsysvia()
{
	clearkeys();
	sysvia.ifr=sysvia.ier=0;
	sysvia.t1c=sysvia.t1l=0x1FFFE;
	sysvia.t2c=sysvia.t2l=0x1FFFE;
	sysvia.t1hit=sysvia.t2hit=0;
	if (autoboot)
	   bbckey[0][0]=1;
}


void clearkeys()
{
	memset(keys, 0, sizeof(keys));
	memset(bbckey, 0, sizeof(bbckey));
}

void checkkeys()
{
	int c,d;
	int row,col;
	int rc;
	memcpy(bbckey, keys, sizeof(bbckey));
	//for (c=0 ; c<16 ; c++) {
	//	for (d=0 ; d<16 ; d++) {
	//		bbckey[c][d]=keys[c][d];
	//	}
	//}
    if (autoboot)
       bbckey[0][0] = 1;
    updatekeyboard();
}


/*
 * updatekeyboard - scans bbckey[] and updates sysvia registers accordingly
 */
void updatekeyboard()
{
	int c,d;
	if (IC32&8) {
		for (d=0;d<(/*(MASTER)?13:*/10);d++) {
			for (c=1;c<8;c++) {
				if (bbckey[d][c]) {
//					LOGI("keydown 0x%X", (c<<8)|d);
					sysca2high();
					return;
				}
			}
		}
		sysca2low();
	}
	else {
		if (keycol<(/*(MASTER)?13:*/10)) {
			for (c=1;c<8;c++) {
				if (bbckey[keycol][c]) {
//					LOGI("keydown 0x%X", (c<<8)|d);
					sysca2high();
					return;
				}
			}
		}
		sysca2low();
	}
}

/*
void initDIPS(uint8_t dips)
{
        int c;
        for (c=9;c>=2;c--)
        {
                if (dips&1)
                   presskey(0,c);
                else
                   releasekey(0,c);
                dips>>=1;
        }
}
*/





uint8_t* save_sysvia(uint8_t* p)
{
	*p++ = sysvia.ora;
	*p++ = sysvia.orb;
	*p++ = sysvia.ira;
	*p++ = sysvia.irb;
	*p++ = sysvia.porta;
	*p++ = sysvia.portb;
	*p++ = sysvia.ddra;
	*p++ = sysvia.ddrb;
	*p++ = sysvia.sr;
	*p++ = sysvia.acr;
	*p++ = sysvia.pcr;
	*p++ = sysvia.ifr;
	*p++ = sysvia.ier;
	*(uint32_t*)p = sysvia.t1l; p+=4;
	*(uint32_t*)p = sysvia.t2l; p+=4;
	*(uint32_t*)p = sysvia.t1c; p+=4;
	*(uint32_t*)p = sysvia.t2c; p+=4;
	*p++ = sysvia.t1hit;
	*p++ = sysvia.t2hit;
	*p++ = sysvia.ca1;
	*p++ = sysvia.ca2;
	*p++ = IC32;
	return p;
}

uint8_t* load_sysvia(uint8_t* p)
{
	sysvia.ora=*p++;
	sysvia.orb=*p++;
	sysvia.ira=*p++;
	sysvia.irb=*p++;
	sysvia.porta=*p++;
	sysvia.portb=*p++;
	sysvia.ddra=*p++;
	sysvia.ddrb=*p++;
	sysvia.sr=*p++;;
	sysvia.acr=*p++;
	sysvia.pcr=*p++;
	sysvia.ifr=*p++;
	sysvia.ier=*p++;
	sysvia.t1l=*(uint32_t*)p; p+=4;
	sysvia.t2l=*(uint32_t*)p; p+=4;
	sysvia.t1c=*(uint32_t*)p; p+=4;
	sysvia.t2c=*(uint32_t*)p; p+=4;
	sysvia.t1hit=*p++;
	sysvia.t2hit=*p++;
	sysvia.ca1=*p++;
	sysvia.ca2=*p++;

	IC32=*p++;
	scrsize=((IC32&16)?2:0)|((IC32&32)?1:0);
	return p;
}


