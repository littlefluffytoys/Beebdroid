/*
 * video.c
 *
 * Video emulation, incorporates 6845 CRTC, Video ULA and SAA5050
 *
 * Original code by Tom Walker, heavily hacked about and optimized by Reuben Scratton.
 *
 */

#include "main.h"
#include "bbctext.h"


BITMAP *b;
int dcol;
int pal[256];
extern unsigned short *current_palette16;
uint8_t mode7chars[96*160];
uint8_t mode7charsi[96*160];
uint8_t mode7graph[96*160];
uint8_t mode7graphi[96*160];
uint8_t mode7sepgraph[96*160];
uint8_t mode7sepgraphi[96*160];
uint8_t mode7tempi[96*120];
uint8_t mode7tempi2[96*120];
extern int collook[8];
extern int fskipcount;
int collook[8];
uint32_t col0;
uint8_t m7lookup[8][8][16];
int vsynctime;
uint16_t ma,maback;
int interline;
int vdispen,dispen;
int hvblcount;
int frameodd;
int con,cdraw,coff;
int cursoron;
int frcount;
int charsleft;
int hc,vc,sc,vadj;
uint8_t crtc[32];
uint8_t crtcmask[32]={0xFF,0xFF,0xFF,0xFF,0x7F,0x1F,0x7F,0x7F,0xF3,0x1F,0x7F,0x1F,0x3F,0xFF,0x3F,0xFF,0x3F,0xFF};
int crtci;
int screenlen[4]={0x4000,0x5000,0x2000,0x2800};
int s_firstx, s_lastx, s_firsty, s_lasty;
uint8_t ulactrl;
uint16_t ulapal[16];
uint8_t bakpal[16];
int ulamode;
int crtcmode;
uint32_t pixlookuph[4][256][2];
uint32_t pixlookupl[4][256][4];
uint8_t table4bpp[4][256][16];
int inverttbl[256];
int vidclocks=0;
int vidbytes=0;
int scrx,scry;
int mode7col=7,mode7bg=0;
uint8_t m7buf[2];
uint8_t *mode7p[2]={mode7chars,mode7charsi};
int mode7sep=0;
int mode7dbl,mode7nextdbl,mode7wasdbl;
int mode7gfx;
int mode7flash,m7flashon=0,m7flashtime=0;
uint8_t heldchar,holdchar;
char *heldp[2];
int interlline=0,oldr8;
int firstx,firsty,lastx,lasty;
uint8_t cursorlook[7]={0,0,0,0x80,0x40,0x20,0x20};
int cdrawlook[4]={3,2,1,0};
int cmask[4]={0,0,16,32};
int lasthc0=0,lasthc;
int oddclock=0;



#define setPixel(y, x, val) 	*(uint16_t*)(b->pixels + (y)*b->stride + (x)*2) = current_palette16[val]
#define invertPixel(y, x) *(uint16_t*)(b->pixels + (y)*b->stride + (x)*2) ^= 0xffff

int makecol(unsigned char r, unsigned char g, unsigned char b) {
	return (b>>6) | ((g>>5)<<2) | ((r>>5)<<5);
}


void doblit()
{
	lasty++;

	// Centers the image, AFAICS
	int c=(lastx+firstx)/2;
	s_firstx=c-336;
	s_lastx=c+336;
	c=(lasty+firsty)/2;
	s_firsty=c-136;
	s_lasty=c+136;

	blit_to_screen(s_firstx,s_firsty, s_lastx-s_firstx, s_lasty-s_firsty);
	firstx=firsty=65535;
	lastx=lasty=0;
}

void resetcrtc()
{
        hc=vc=sc=vadj=0;
        interline=0;
        vsynctime=0;
        hvblcount=0;
        frameodd=0;
        con=cdraw=0;
        cursoron=0;
        charsleft=0;
        crtc[9]=10;
}

void writecrtc(uint16_t addr, uint8_t val)
{
	if (!(addr&1))
		crtci=val&31;
	else {
		crtc[crtci]=val&crtcmask[crtci];
	}
}

uint8_t readcrtc(uint16_t addr)
{
        if (!(addr&1)) return crtci;
        return crtc[crtci];
}



void writeula(uint16_t addr, uint8_t val)
{
	int c;
	if (!(addr&1)) {
		if ((ulactrl^val)&1) {
			if (val&1) {
				for (c=0;c<16;c++) {
					if (bakpal[c]&8) ulapal[c]=current_palette16[collook[bakpal[c]&7]];
					else             ulapal[c]=current_palette16[collook[(bakpal[c]&7)^7]];
				}
			}
			else {
				for (c=0;c<16;c++)
					ulapal[c]=current_palette16[collook[(bakpal[c]&7)^7]];
			}
		}
		ulactrl=val;
		ulamode=(ulactrl>>2)&3;

		if (val&2)         crtcmode=0;
		else if (val&0x10) crtcmode=1;
		else               crtcmode=2;

	}
	else {
		c=bakpal[val>>4];
		bakpal[val>>4]=val&15;
		ulapal[val>>4]=current_palette16[collook[(val&7)^7]];
		if (val&8 && ulactrl&1) ulapal[val>>4]=current_palette16[collook[val&7]];
    }
}




void initvideo()
{
        int c,d;
        int temp,temp2,left;

        generate_332_palette(pal);
        set_palette(pal);
        collook[0]=makecol(0,0,0);
        col0=collook[0]|(collook[0]<<8)|(collook[0]<<16)|(collook[0]<<24);
        collook[1]=makecol(255,0,0);
        collook[2]=makecol(0,255,0);
        collook[3]=makecol(255,255,0);
        collook[4]=makecol(0,0,255);
        collook[5]=makecol(255,0,255);
        collook[6]=makecol(0,255,255);
        collook[7]=makecol(255,255,255);

        for (c=0;c<16;c++)
        {
                for (d=0;d<64;d++)
                {
                        m7lookup[d&7][(d>>3)&7][c]=makecol(     (((d&1)*c)*255)/15 + ((((d&8)>>3)*(15-c))*255)/15,
                                                            ((((d&2)>>1)*c)*255)/15 + ((((d&16)>>4)*(15-c))*255)/15,
                                                            ((((d&4)>>2)*c)*255)/15 + ((((d&32)>>5)*(15-c))*255)/15);
                }
        }

        for (temp=0;temp<256;temp++)
        {
                temp2=temp;
                for (c=0;c<16;c++)
                {
                     left=0;
                     if (temp2&2)
                        left|=1;
                     if (temp2&8)
                        left|=2;
                     if (temp2&32)
                        left|=4;
                     if (temp2&128)
                        left|=8;
                     table4bpp[3][temp][c]=left;
                     temp2<<=1; temp2|=1;
                }
                for (c=0;c<16;c++)
                {
                        table4bpp[2][temp][c]=table4bpp[3][temp][c>>1];
                        table4bpp[1][temp][c]=table4bpp[3][temp][c>>2];
                        table4bpp[0][temp][c]=table4bpp[3][temp][c>>3];
                }
        }

        b = create_bitmap(1024,512); // GL-friendly size

        clear_to_color(b,collook[0]);
        for (c=0;c<256;c++) {
        	inverttbl[c]=makecol((63-RED(pal[c]))<<2,(63-GREEN(pal[c]))<<2,(63-BLUE(pal[c]))<<2);
        }
}


void makemode7chars()
{
        int c,d,y;
        int offs1=0,offs2=0;
        float x;
        int x2;
        int stat;
        uint8_t *p=teletext_characters,*p2=mode7tempi;

        for (c=0;c<(96*120);c++) mode7tempi2[c]=teletext_characters[c>>1];
        for (c=0;c<960;c++)
        {
                x=0;
                x2=0;
                for (d=0;d<16;d++)
                {
                        mode7graph[offs2+d]=(int)(((float)teletext_graphics[offs1+x2]*(1.0-x))+((float)teletext_graphics[offs1+x2+1]*x));
                        mode7sepgraph[offs2+d]=(int)(((float)teletext_separated_graphics[offs1+x2]*(1.0-x))+((float)teletext_separated_graphics[offs1+x2+1]*x));
                        if (!d)
                        {
                                mode7graph[offs2+d]=mode7graphi[offs2+d]=teletext_graphics[offs1];
                                mode7sepgraph[offs2+d]=mode7sepgraphi[offs2+d]=teletext_separated_graphics[offs1];
                        }
                        else if (d==15)
                        {
                                mode7graph[offs2+d]=mode7graphi[offs2+d]=teletext_graphics[offs1+5];
                                mode7sepgraph[offs2+d]=mode7sepgraphi[offs2+d]=teletext_separated_graphics[offs1+5];
                        }
                        else
                        {
                                mode7graph[offs2+d]=mode7graphi[offs2+d]=teletext_graphics[offs1+x2];
                                mode7sepgraph[offs2+d]=mode7sepgraphi[offs2+d]=teletext_separated_graphics[offs1+x2];
                        }
                        x+=(5.0/15.0);
                        if (x>=1.0)
                        {
                                x2++;
                                x-=1.0;
                        }
                        mode7charsi[offs2+d]=0;
                }
                
                offs1+=6;
                offs2+=16;
        }
        for (c=0;c<96;c++)
        {
                for (y=0;y<10;y++)
                {
                        for (d=0;d<6;d++)
                        {
                                stat=0;
                                if (y<9 && p[(y*6)+d] && p[(y*6)+d+6]) stat=3; /*Above + below - set both*/
                                if (y<9 && d>0 && p[(y*6)+d] && p[(y*6)+d+5] && !p[(y*6)+d-1]) stat|=1; /*Above + left - set left*/
                                if (y<9 && d>0 && p[(y*6)+d+6] && p[(y*6)+d-1] && !p[(y*6)+d+5]) stat|=1; /*Below + left - set left*/
                                if (y<9 && d<5 && p[(y*6)+d] && p[(y*6)+d+7] && !p[(y*6)+d+1]) stat|=2; /*Above + right - set right*/
                                if (y<9 && d<5 && p[(y*6)+d+6] && p[(y*6)+d+1] && !p[(y*6)+d+7]) stat|=2; /*Below + right - set right*/
                                p2[0]=(stat&1)?15:0;
                                p2[1]=(stat&2)?15:0;
                                p2+=2;
                        }
                }
                p+=60;
        }
        offs1=offs2=0;
        for (c=0;c<960;c++)
        {
                x=0;
                x2=0;
                for (d=0;d<16;d++)
                {
                        mode7chars[offs2+d]=(int)(((float)mode7tempi2[offs1+x2]*(1.0-x))+((float)mode7tempi2[offs1+x2+1]*x));
                        mode7charsi[offs2+d]=(int)(((float)mode7tempi[offs1+x2]*(1.0-x))+((float)mode7tempi[offs1+x2+1]*x));
                        x+=(11.0/15.0);
                        if (x>=1.0)
                        {
                                x2++;
                                x-=1.0;
                        }
                        if (c>=320 && c<640)
                        {
                                mode7graph[offs2+d]=mode7sepgraph[offs2+d]=mode7chars[offs2+d];
                                mode7graphi[offs2+d]=mode7sepgraphi[offs2+d]=mode7charsi[offs2+d];
                        }
                }
                offs1+=12;
                offs2+=16;
        }
}


static inline void rendermode7(uint8_t dat)
{
        int t;
        int off;
        int mcolx=mode7col;
        int holdoff=0,holdclear=0;
        char *mode7px[2];
        int mode7flashx=mode7flash,mode7dblx=mode7dbl;
        uint8_t *on;
        
        t=m7buf[0];
        m7buf[0]=m7buf[1];
        m7buf[1]=dat;
        dat=t;
        mode7px[0]=mode7p[0];
        mode7px[1]=mode7p[1];
        
        if (dat==255) {
        	int c;
			for (c=0;c<16;c++) {
				setPixel(scry, scrx+c+16, collook[0]);
			}
            return;
        }

        if (!mode7dbl && mode7nextdbl)
        	on=m7lookup[mode7bg&7][mode7bg&7];

        if (dat<0x20)
        {
                switch (dat)
                {
                        case 1: case 2: case 3: case 4: case 5: case 6: case 7:
                        mode7gfx=0;
                        mode7col=dat;
                        mode7p[0]=mode7chars;
                        mode7p[1]=mode7charsi;
                        holdclear=1;
                        break;
                        case 8: mode7flash=1; break;
                        case 9: mode7flash=0; break;
                        case 12: case 13:
                        mode7dbl=dat&1;
                        if (mode7dbl) mode7wasdbl=1;
                        break;
                        case 17: case 18: case 19: case 20: case 21: case 22: case 23:
                        mode7gfx=1;
                        mode7col=dat&7;
                        if (mode7sep) {
							mode7p[0]=mode7sepgraph;
							mode7p[1]=mode7sepgraphi;
                        }
                        else {
							mode7p[0]=mode7graph;
							mode7p[1]=mode7graphi;
                        }
                        break;
                        case 24: mode7col=mcolx=mode7bg; break;
                        case 25: if (mode7gfx) { mode7p[0]=mode7graph;    mode7p[1]=mode7graphi;    } mode7sep=0; break;
                        case 26: if (mode7gfx) { mode7p[0]=mode7sepgraph; mode7p[1]=mode7sepgraphi; } mode7sep=1; break;
                        case 28: mode7bg=0; break;
                        case 29: mode7bg=mode7col; break;
                        case 30: holdchar=1; break;
                        case 31: holdoff=1; break;
                }

                if (holdchar) {
					dat=heldchar;
					if (dat>=0x40 && dat<0x60) dat=32;
					mode7px[0]=heldp[0];
					mode7px[1]=heldp[1];
                }
                else
                   dat=0x20;
                if (mode7dblx!=mode7dbl) dat=32; /*Double height doesn't respect held characters*/
    }
	else if (mode7p[0]!=mode7chars) {
		heldchar=dat;
		heldp[0]=mode7px[0];
		heldp[1]=mode7px[1];
	}

	if (mode7dblx && !mode7nextdbl) t=((dat-0x20)*160)+((sc>>1)*16);
	else if (mode7dblx)             t=((dat-0x20)*160)+((sc>>1)*16)+(5*16);
	else                           t=((dat-0x20)*160)+(sc*16);

	off=m7lookup[0][mode7bg&7][0];
	if (!mode7dbl && mode7nextdbl) on=m7lookup[mode7bg&7][mode7bg&7];
	else                           on=m7lookup[mcolx&7][mode7bg&7];

	int x = scrx+16;
	if (mode7flashx && !m7flashon) {
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
		setPixel(scry, x, off); x++;
	}
	else if (mode7dblx) {
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
		setPixel(scry, x, on[mode7px[sc&1][t]&15]); x++; t++;
	}
	else {
		uint16_t* p = (uint16_t*)(b->pixels + scry*b->stride + x*2);// = current_palette16[val]
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
		*p++ = current_palette16[on[mode7px[0][t++]&15]];
	}


	if ((scrx+16)<firstx) firstx=scrx+16;
	if ((scrx+32)>lastx) {
		lastx=scrx+32;
	}
	if (holdoff) {
		holdchar=0;
		heldchar=32;
	}
	if (holdclear) heldchar=32;
}


void latchpen()
{
        crtc[0x10]=(ma>>8)&0x3F;
        crtc[0x11]=ma&0xFF;
}


inline void resetPixelQuad(int y, int x) {
	uint32_t* p = (uint32_t*)(b->pixels + y * b->stride + x*2);
	uint32_t col0 = current_palette16[0];
	col0 = (col0<<16) | col0;
	*p++ = col0;
	*p++ = col0;
}


void pollvideo(int clocks)
{
	int c,oldvc;
	uint16_t addr;
	uint8_t dat;

	while (clocks--) {

		scrx+=8;
		vidclocks++;
		oddclock=!oddclock;
		if (!(ulactrl&0x10) && !oddclock)
			continue;


		if (hc==crtc[1]) {
			if (ulactrl&2 && dispen) charsleft=3;
			else charsleft=0;
			dispen=0;
		}

		// If reached end of current scanline?
		if (hc==crtc[2]) {
			if (ulactrl&0x10) scrx=128-((crtc[3]&15)*4);
			else              scrx=128-((crtc[3]&15)*8);
			scry++;
			if (scry>=384) {
				scry=0;
				doblit();
			}
		}


		if (dispen) {

			if (!((ma^(crtc[15]|(crtc[14]<<8)))&0x3FFF) && con)
				cdraw=cdrawlook[crtc[8]>>6];
			if (ma&0x2000)
				dat = the_cpu->mem[0x7C00|(ma&0x3FF)];
			else {
				if ((crtc[8]&3)==3) addr=(ma<<3)|((sc&3)<<1)|interlline;
				else           addr=(ma<<3)|(sc&7);
				if (addr&0x8000) addr-=screenlen[scrsize];
				dat = the_cpu->mem[(addr&0x7FFF)];
			}

			if (scrx<1024) {
				if ((crtc[8]&0x30)==0x30 || ((sc&8) && !(ulactrl&2))) {
					for (c=0;c<((ulactrl&0x10)?8:16);c+=4) {
						resetPixelQuad(scry, scrx+c);
					}
				}
				else {
					register uint32_t* p;
					register uint8_t* q;

					switch (crtcmode) {
					case 0:
						rendermode7(dat&0x7F);
						break;
					case 1:
						if (scrx<firstx) firstx=scrx;
						if ((scrx+8)>lastx) lastx=scrx+8;

						// Optimized palette blit
						q = table4bpp[ulamode][dat];
						p = (uint32_t*)(b->pixels + scry * b->stride + scrx*2);
						*p++ =  ulapal[q[0]]
						     | (ulapal[q[1]] << 16);
						*p++ =  ulapal[q[2]]
						     | (ulapal[q[3]] << 16);
						*p++ =  ulapal[q[4]]
						     | (ulapal[q[5]] << 16);
						*p++ =  ulapal[q[6]]
						     | (ulapal[q[7]] << 16);
						break;
					case 2:
						if (scrx<firstx) firstx=scrx;
						if ((scrx+16)>lastx) lastx=scrx+16;
						// Optimized palette blit
						p = (uint32_t*)(b->pixels + scry * b->stride + scrx*2);
						q = table4bpp[ulamode][dat];
						//for (c=0;c<16; c+=4)   {
							*p++ =  ulapal[q[0]]
							     | (ulapal[q[1]] << 16);
							*p++ =  ulapal[q[2]]
							     | (ulapal[q[3]] << 16);
							*p++ =  ulapal[q[4]]
							     | (ulapal[q[5]] << 16);
							*p++ =  ulapal[q[6]]
							     | (ulapal[q[7]] << 16);
							*p++ =  ulapal[q[8]]
							     | (ulapal[q[9]] << 16);
							*p++ =  ulapal[q[10]]
							     | (ulapal[q[11]] << 16);
							*p++ =  ulapal[q[12]]
							     | (ulapal[q[13]] << 16);
							*p++ =  ulapal[q[14]]
							     | (ulapal[q[15]] << 16);
						//	}
						break;
					}
				}

				// Cursor drawing
				if (cdraw) {
					if (cursoron && (ulactrl & cursorlook[cdraw])) {
						for (c=0;c<((ulactrl&0x10)?8:16);c++) {
							invertPixel(scry, scrx+c);
						}
					}
					cdraw++;
					if (cdraw==7) cdraw=0;
				}
			}
			ma++;
			vidbytes++;
		}

		else {
			if (charsleft) {
				if (charsleft!=1) rendermode7(255);
				charsleft--;
			}
			else if (scrx<1024) {
				for (c=0;c<((ulactrl&0x10)?8:16);c+=4) {
					resetPixelQuad(scry, scrx+c);
				}
				if (!crtcmode) {
					for (c=0;c<16;c+=4)  {
						resetPixelQuad(scry, scrx+c+16);
					}
				}
			}
			if (cdraw && scrx<1024) {
				if (cursoron && (ulactrl & cursorlook[cdraw])) {
					for (c=0;c<((ulactrl&0x10)?8:16);c++) {
						invertPixel(scry, scrx+c);
					}
                }
				cdraw++;
				if (cdraw==7) cdraw=0;
			}
		}

		if (hvblcount) {
			hvblcount--;
			if (!hvblcount) {
					vblankintlow();
			}
		}

		if (interline && hc==(crtc[0]>>1)) {
			hc=interline=0;
			lasthc0=1;
			if (ulactrl&0x10) scrx=128-((crtc[3]&15)*4);
			else              scrx=128-((crtc[3]&15)*8);
		}
		else if (hc==crtc[0]) {
			mode7col=7;
			mode7bg=0;
			holdchar=0;
			heldchar=0x20;
			mode7p[0]=mode7chars;
			mode7p[1]=mode7charsi;
			mode7flash=0;
			mode7sep=0;
			mode7gfx=0;
			heldchar=32;
			heldp[0]=mode7p[0];
			heldp[1]=mode7p[1];

			hc=0;
			if (sc==(crtc[11]&31) || ((crtc[8]&3)==3 && sc==((crtc[11]&31)>>1))) {
				con=0;
				coff=1;
			}
			if (vadj) {
				sc++;
				sc&=31;
				ma=maback;
				vadj--;
				if (!vadj) {
					vdispen=1;
					ma=maback=(crtc[13]|(crtc[12]<<8))&0x3FFF;
					sc=0;
				}
			}
			else if (sc==crtc[9] || ((crtc[8]&3)==3 && sc==(crtc[9]>>1))) {
				maback=ma;
				sc=0;
				con=0;
				coff=0;
				if (mode7nextdbl) mode7nextdbl=0;
				else              mode7nextdbl=mode7wasdbl;
				oldvc=vc;
				vc++;
				vc&=127;
				if (vc==crtc[6]) vdispen=0;
				if (oldvc==crtc[4]) {
					vc=0;
					vadj=crtc[5];
					if (!vadj) vdispen=1;
					if (!vadj) ma=maback=(crtc[13]|(crtc[12]<<8))&0x3FFF;

					frcount++;
					if (!(crtc[10]&0x60)) cursoron=1;
					else                 cursoron=frcount&cmask[(crtc[10]&0x60)>>5];
				}
				if (vc==crtc[7]) {
					if (!(crtc[8]&1) && oldr8) clear_to_color(b,col0);
					frameodd^=1;
					if (frameodd) interline=(crtc[8]&1);
					interlline=frameodd && (crtc[8]&1);
					oldr8=crtc[8]&1;
					if (vidclocks>2) {
						doblit();
					}
					scry=0;
					vblankint();
					vsynctime=(crtc[3]>>4)+1;
					if (!(crtc[3]>>4)) vsynctime=17;
					m7flashtime++;
					if ((m7flashon && m7flashtime==32) || (!m7flashon && m7flashtime==16))  {
						m7flashon=!m7flashon;
						m7flashtime=0;
					}
					vidclocks=vidbytes=0;
				}
            }
            else  {
				sc++;
				sc&=31;
				ma=maback;
			}
                        
			mode7dbl=mode7wasdbl=0;
			if ((sc==(crtc[10]&31) || ((crtc[8]&3)==3 && sc==((crtc[10]&31)>>1))) && !coff) con=1;
                        
			if (vsynctime) {
				vsynctime--;
				if (!vsynctime)	{
					hvblcount=1;
					if (frameodd) interline=(crtc[8]&1);
				}
			}
                        
			dispen=vdispen;
			if (dispen || vadj) {
				if (scry<firsty) firsty=scry;
				if ((scry+1)>lasty) lasty=scry;
			}
			lasthc0=1;
                        

			if (adcconvert) {
				adcconvert--;
				if (!adcconvert) polladc();
			}
        }
        else {
			hc++;
			hc&=255;
		}
		lasthc=hc;
	}
}



uint8_t* save_video(uint8_t* p)
{
	int c;
	*p++ = ulactrl;
    for (c=0;c<16;c++) *p++=bakpal[c];
	for (c=0;c<18;c++) *p++ = crtc[c];
	*p++ = vc;
	*p++ = sc;
	*p++ = hc;
	*(uint16_t*)p = ma; p+=2;
	*(uint16_t*)p = maback; p+=2;
	*(uint16_t*)p = scrx; p+=2;
	*(uint16_t*)p = scry; p+=2;
	*p++ = oddclock;
	*(uint32_t*)p = vidclocks; p += 4;
	return p;
}

uint8_t* load_video(uint8_t* p)
{
	int c;
	writeula(0, *p++);
	for (c=0;c<16;c++) writeula(1, (*p++) |(c<<4));
	for (c=0;c<18;c++) crtc[c]=*p++;
	vc=*p++;
	sc=*p++;
	hc=*p++;
	ma=*(uint16_t*)p; p+=2;
	maback=*(uint16_t*)p; p+=2;
	scrx=*(uint16_t*)p; p+=2;
	scry=*(uint16_t*)p; p+=2;
	oddclock=*p++;
	vidclocks=*(uint32_t*)p; p+= 4;
	return p;
}
