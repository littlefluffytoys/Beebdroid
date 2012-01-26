/*B-em v2.1 by Tom Walker
  Internal SN sound chip emulation*/

#include "main.h"

//#define BUFLEN 3125
#define BUFLEN 2000
//#define BUFLEN (882*2)


uint16_t snshift;

#define NOISEBUFFER 32768
static int8_t snperiodic[2][NOISEBUFFER];
int rectpos=0,rectdir=0;
int vgmsamples;

void fillbuf(int16_t *buf, int len)
{
	memset(buf,0,len*2);
}


float volslog[16]=
{
	0.00000f,0.59715f,0.75180f,0.94650f,
	1.19145f,1.50000f,1.88835f,2.37735f,
	2.99295f,3.76785f,4.74345f,5.97165f,
	7.51785f,9.46440f,11.9194f,15.0000f
};

int volume_table[16]={
  32767, 26028, 20675, 16422, 13045, 10362,  8231,  6568,
   5193,  4125,  3277,  2603,  2067,  1642,  1304,     0
};



uint8_t snfreqhi[4],snfreqlo[4];
uint8_t snvol[4];
uint8_t snnoise;
int lasttone;
int soundon=1;

int16_t snwaves[5][32]=
{
        {
	        127,127,127,127,127,127,127,127,127,127,127,127,127,127,127,127,
                -127,-127,-127,-127,-127,-127,-127,-127,-127,-127,-127,-127,-127,-127,-127,-127

        },
        {
	        -120,-112,-104,-96,-88,-80,-72,-64,-56,-48,-40,-32,-24,-16,-8,0,
	        8,16,24,32,40,48,56,64,72,80,88,96,104,112,120,127
        },
        {
                16,32,48,64,80,96,112,128,112,96,80,64,48,32,16,0,
                -16,-32,-48,-64,-80,-96,-112,-128,-112,-96,-80,-64,-48,-32,-16,0
        },
        {
                0,24,48,70,89,105,117,124,126,124,117,105,89,70,48,24,0,
                -24,-48,-70,-89,-105,-117,-124,-126,-124,-117,-105,-90,-70,-48,-25,
        }
};

void updaterectwave(int d)
{
        int c;
        c>>=3;
        for (c=0;c<d;c++) snwaves[4][c]=snperiodic[1][c]=127;
        for (;c<32;c++)   snwaves[4][c]=snperiodic[1][c]=-127;
}


int soundinited=0;
int soundiniteded;
static int8_t snperiodic[2][NOISEBUFFER];
static int8_t snperiodic2[32] =
{
       127,127,127,127,127,127,127,127,127,127,127,127,127,127,127,127,
       127,127,127,127,127,127,127,127,127,127,127,127,127,127,0,0
};


fixed sncount[4],snstat[4];
uint32_t snlatch[4];
int snvols[3125<<1][4],snnoise2[3125<<1];
fixed snlatchs[3125<<1][4];
int snline=0,snlinec=0;


void updatebuffer(int16_t *buffer, int len);

void resetsound()
{
    snline=0;
}

extern int cbSndbuf;
extern short* sndbuf;

//
// logvols - called every 128 clocks from 6502.c
//
void logvols()
{
        int c;
        snvols[snline][0]=snvol[0];
        snvols[snline][1]=snvol[1];
        snvols[snline][2]=snvol[2];
        snvols[snline][3]=snvol[3];
        snlatchs[snline][0]=(snlatch[0])?snlatch[0]:1024;
        snlatchs[snline][1]=(snlatch[1])?snlatch[1]:1024;
        snlatchs[snline][2]=(snlatch[2])?snlatch[2]:1024;
        snlatchs[snline][3]=(snlatch[3])?snlatch[3]:1024;
        snnoise2[snline]=snnoise;
        sndbuf[snline] = 0;
        snline++;
        if (snline==(BUFLEN>>1)) { // If buffer half full?
                snline=0;
                updatebuffer(sndbuf, BUFLEN);
                givealbuffer(sndbuf, 0, BUFLEN*sizeof(short));
        }
}

#define NCoef 4

float ACoef[NCoef+1] = {
        0.30631912757971225000,
        0.00000000000000000000,
        -0.61263825515942449000,
        0.00000000000000000000,
        0.30631912757971225000
    };

    float BCoef[NCoef+1] = {
        1.00000000000000000000,
        -1.86772356053227330000,
        1.08459167506874430000,
        -0.37711292573951394000,
        0.17253125052500490000
    };
float iir(float NewSample) {


    static float y[NCoef+1]; //output samples
    static float x[NCoef+1]; //input samples
    int n;
    
    //shift the old samples
    for(n=NCoef; n>0; n--) {
       x[n] = x[n-1];
       y[n] = y[n-1];
    }

    //Calculate the new output
    x[0] = NewSample;
    y[0] = ACoef[0] * x[0];
    for(n=1; n<=NCoef; n++)
        y[0] += ACoef[n] * x[n] - BCoef[n] * y[n];

    return y[0];
}



void updatebuffer(int16_t *buffer, int numSamples)
{
	int c,d,diff[1024],e;
	uint8_t oldlast[4];
	float tempf,tempf2;
	uint16_t *sbuf=buffer;
	int lcount=0;
	memset(buffer,0,numSamples*2);

	for (d=0;d<numSamples;d++) {

		// For each tone generator on the chip
        for (c=0;c<3;c++) {
			c++;
			if (snlatchs[d>>1][c]>256) buffer[d]+=(snwaves[0][snstat[c]]*volslog[snvols[d>>1][c]]);
			else                       buffer[d]+=volslog[snvols[d>>1][c]]*127;
			sncount[c]-=8192;
			while ((int)sncount[c]<0  && snlatchs[d>>1][c]) {
				sncount[c]+=snlatchs[d>>1][c];
				snstat[c]++;
				snstat[c]&=31;
			}
			c--;
        }

		if (!(snnoise2[d>>1]&4)) {
			/*if (curwave==4) buffer[d]+=(snperiodic[1][snstat[0]&31]*volslog[snvols[d>>1][0]]);
			else*/            buffer[d]+=(((snshift&1)^1)*127*volslog[snvols[d>>1][0]]*2);
		}
		else  {
			buffer[d]+=(((snshift&1)^1)*127*volslog[snvols[d>>1][0]]*2);
		}
		sncount[0]-=512;
		while ((int)sncount[0]<0 && snlatchs[d>>1][0]) {
			sncount[0]+=(snlatchs[d>>1][0]*2);
			if (!(snnoise2[d>>1]&4)) {
				if (snshift&1) snshift|=0x8000;
				snshift>>=1;
			}
			else {
				if ((snshift&1)^((snshift>>1)&1)) snshift|=0x8000;
				snshift>>=1;
			}
			snstat[0]++;
		}
		if (!(snnoise2[d>>1]&4)) {
			while (snstat[0]>=30) snstat[0]-=30;
		}
		else
		   snstat[0]&=32767;
		buffer[d]=(int)iir((float)buffer[d]);
	}

	for (d=0;d<numSamples;d++) {
		sbuf[d]*=2;
	}

}

void initsound()
{
        int c;
        for (c=0;c<16;c++) volslog[c]=(float)volume_table[15-c]/2048.0;

        soundiniteded=1;
        for (c=0;c<NOISEBUFFER;c++)
            snperiodic[0][c]=snperiodic2[c&31];
        for (c=0;c<32;c++)
            snwaves[3][c]-=128;


        snlatch[0]=snlatch[1]=snlatch[2]=snlatch[3]=0x3FF<<6;
        snvol[0]=0;
        snvol[1]=snvol[2]=snvol[3]=8;
        srand(time(NULL));
        sncount[0]=0;
        sncount[1]=(rand()&0x3FF)<<6;
        sncount[2]=(rand()&0x3FF)<<6;
        sncount[3]=(rand()&0x3FF)<<6;
        snnoise=3;
        snshift=0x4000;
        
        soundinited=1;
}

int vgmpos=0;
uint8_t firstdat;
uint8_t lastdat;

//
// writesound() - called when the CPU writes a byte to the sound chip.
//
/*

The sound generator is programmed by sending it bytes in the following format:-

	23.4.1 Frequency (First byte)

			Register Address		Data
	Bit 	7    6    5    4    3    2    1    0
			1   R2   R1   R0   F3   F2   F1   F0

	23.4.2 Frequency (Second byte)
									Data
	Bit 	7    6    5    4    3    2    1    0
			0	 X   F9   F8   F7   F6   F5   F4

Note that the second low order frequency byte may be continually updated without rewriting the first byte.


23.4.3	Noise source byte Register Address
Bit 7 6 5 4 3 2 1 0 1 R2 R1 R0 X FB NF1 NF0
23.4.4	Update volume level Register Address	Data
Bit 7 6 5 4 3 2 1 0 1 R2 R1 R0 A3 A2 A1 A0
 */
void writesound(uint8_t data)
{
        int freq;
        int c;
        lastdat=data;

        if (data&0x80)
        {
                firstdat=data;
                switch (data&0x70)
                {
                        case 0:
                        snfreqlo[3]=data&0xF;
                        snlatch[3]=(snfreqlo[3]|(snfreqhi[3]<<4))<<6;
                        lasttone=3;
                        break;
                        case 0x10:
                        data&=0xF;
                        snvol[3]=0xF-data;
                        break;
                        case 0x20:
                        snfreqlo[2]=data&0xF;
                        snlatch[2]=(snfreqlo[2]|(snfreqhi[2]<<4))<<6;
                        lasttone=2;
                        break;
                        case 0x30:
                        data&=0xF;
                        snvol[2]=0xF-data;
                        break;
                        case 0x40:
                        snfreqlo[1]=data&0xF;
                        snlatch[1]=(snfreqlo[1]|(snfreqhi[1]<<4))<<6;
                        lasttone=1;
                        break;
                        case 0x50:
                        data&=0xF;
                        snvol[1]=0xF-data;
                        break;
                        case 0x60:
                        snshift=0x4000;
                        if ((data&3)!=(snnoise&3)) sncount[0]=0;
                        snnoise=data&0xF;
                        if ((data&3)==3) snlatch[0]=snlatch[1];
                        else             snlatch[0]=0x400<<(data&3);
                        break;
                        case 0x70:
                        data&=0xF;
                        snvol[0]=0xF-data;
                        break;
                }
        }
        else
        {
                if ((firstdat&0x70)==0x60)
                {
                        snshift=0x4000;
                        if ((data&3)!=(snnoise&3)) sncount[0]=0;
                        snnoise=data&0xF;
                        if ((data&3)==3) snlatch[0]=snlatch[1];
                        else             snlatch[0]=0x400<<(data&3);
                        return;
                }
                snfreqhi[lasttone]=data&0x3F;
                freq=snfreqlo[lasttone]|(snfreqhi[lasttone]<<4);
                if ((snnoise&3)==3&&lasttone==1)
                {
                        snlatch[0]=freq<<6;
                }
                snlatch[lasttone]=freq<<6;
                sncount[lasttone]=0;
        }
}

/*void startsnlog(char *fn)
{
        int c;
        if (snlog)
           fclose(snlog);
        if (snlog2)
           fclose(snlog2);
        vgmsamples=vgmpos=0;
        logging=1;
        snlog=fopen("temp.vgm","wb");
        snlog2=fopen(fn,"wb");
        putc('V',snlog);
        putc('g',snlog);
        putc('m',snlog);
        putc(' ',snlog);
        //We don't know file length yet so just store 0
        putc(0,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
        // Version number - 1.50
        putc(0x50,snlog); putc(1,snlog); putc(0,snlog); putc(0,snlog);
        // Clock speed - 4mhz
        putc(4000000&255,snlog);
        putc(4000000>>8,snlog);
        putc(4000000>>16,snlog);
        putc(4000000>>24,snlog);
        //We don't have an FM chip
        putc(0,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
        //We don't have an GD3 tag
        putc(0,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
        //We don't know total samples
        putc(0,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
        //No looping
        putc(0,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
        putc(0,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
        //50hz. This is true even in NTSC mode as the sound log is always updated at 50hz
        putc(50,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
        //White noise feedback pattern & length
        putc(3,snlog); putc(0,snlog); putc(15,snlog); putc(0,snlog);
        //We don't have an FM chip
        putc(0,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
        //We don't have an FM chip
        putc(0,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
        //Data offset
        putc(0xC,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
        //Reserved
        putc(0,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
        putc(0,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
        putc(0,snlog); putc(0,snlog); putc(0,snlog); putc(0,snlog);
}

void stopsnlog()
{
        int c,len;
        uint8_t buffer[32];
        putc(0x66,snlog);
        len=ftell(snlog);
        fclose(snlog);
        snlog=fopen("temp.vgm","rb");
        for (c=0;c<4;c++) putc(getc(snlog),snlog2);
        putc(len,snlog2);
        putc(len>>8,snlog2);
        putc(len>>16,snlog2);
        putc(len>>24,snlog2);
        for (c=0;c<4;c++) getc(snlog);
        for (c=0;c<16;c++) putc(getc(snlog),snlog2);
        putc(vgmsamples,snlog2);
        putc(vgmsamples>>8,snlog2);
        putc(vgmsamples>>16,snlog2);
        putc(vgmsamples>>24,snlog2);
        for (c=0;c<4;c++) getc(snlog);
        while (!feof(snlog))
        {
                putc(getc(snlog),snlog2);
        }
        fclose(snlog2);
        fclose(snlog);
        remove("temp.vgm");
//        printf("%08X samples\n",vgmsamples);
        logging=0;
}

void logsound()
{
        if (vgmpos) fwrite(vgmdat,vgmpos,1,snlog);
        putc(0x63,snlog);
        vgmsamples+=882;
        vgmpos=0;
}


void savesoundstate(FILE *f)
{
        fwrite(snlatch,16,1,f);
        fwrite(sncount,16,1,f);
        fwrite(snstat,16,1,f);
        fwrite(snvol,4,1,f);
        putc(snnoise,f);
        putc(snshift,f); putc(snshift>>8,f);
}

void loadsoundstate(FILE *f)
{
        fread(snlatch,16,1,f);
        fread(sncount,16,1,f);
        fread(snstat,16,1,f);
        fread(snvol,4,1,f);
        snnoise=getc(f);
        snshift=getc(f); snshift|=getc(f)<<8;
}
*/
