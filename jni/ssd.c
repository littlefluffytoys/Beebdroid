/*B-em v2.1 by Tom Walker
  SSD/DSD disc handling*/
#include "main.h"


struct {
	unsigned char* img;
	int cb;
	int pos;
} ssdf[2];
uint8_t trackinfo[2][2][10*256];
int dsd[2],ssdtrackc[2];
int writeprot[2],fwriteprot[2];

int ssdsector,ssdtrack,ssdside,ssddrive;
int ssdread,ssdwrite,ssdreadpos,ssdreadaddr;
int ssdtime;
int ssdnotfound;
int ssdrsector=0;
int ssdformat=0;

void ssd_reset()
{
	ssdf[0].img=NULL;
	ssdf[0].cb=0;
	ssdf[0].pos=0;
	ssdf[1].img=NULL;
	ssdf[1].cb=0;
	ssdf[1].pos=0;
	dsd[0]=dsd[1]=0;
	ssdnotfound=0;
	ssdsector=ssdtrack=ssdside=ssddrive=0;
	ssdread=ssdwrite=ssdreadpos=ssdreadaddr=0;
	ssdtime=0;
	ssdnotfound=0;
	ssdrsector=0;
	ssdformat=0;
}

void ssd_load(int drive, unsigned char *img, int cb)
{
	LOGI("ssd_load");
        writeprot[drive]=0;
        ssdf[drive].img = img;
        ssdf[drive].cb = cb;
        ssdf[drive].pos = 0;
        dsd[drive]=0;
        drives[drive].seek=ssd_seek;
        drives[drive].readsector=ssd_readsector;
        drives[drive].writesector=ssd_writesector;
        drives[drive].readaddress=ssd_readaddress;
        drives[drive].poll=ssd_poll;
        drives[drive].format=ssd_format;
}

void dsd_load(int drive, unsigned char *img, int cb)
{
	LOGI("dsd_load");
        writeprot[drive]=0;
        ssdf[drive].img = img;
        ssdf[drive].cb = cb;
        ssdf[drive].pos = 0;
        dsd[drive]=1;
        drives[drive].seek=ssd_seek;
        drives[drive].readsector=ssd_readsector;
        drives[drive].writesector=ssd_writesector;
        drives[drive].readaddress=ssd_readaddress;
        drives[drive].poll=ssd_poll;
        drives[drive].format=ssd_format;
}

void ssd_close(int drive)
{
	LOGI("ssd_close");
        ssdf[drive].img=NULL;
        ssdf[drive].cb=0;
        ssdf[drive].pos = 0;
}

#define min(x,y) (((x)<(y)) ? (x) : (y))

void ssd_seek(int drive, int track)
{
        if (!ssdf[drive].img) return;
        LOGI("ssd_seek :%i to %i\n",drive,track);
        ssdtrackc[drive]=track;
        if (dsd[drive])
        {
        	ssdf[drive].pos = track*20*256; //fseek(ssdf[drive],track*20*256,SEEK_SET);
            memcpy(trackinfo[drive][0], ssdf[drive].img+ssdf[drive].pos, 10*256);    //fread(trackinfo[drive][0],10*256,1,ssdf[drive]);
            ssdf[drive].pos += 10*256;
            memcpy(trackinfo[drive][1], ssdf[drive].img+ssdf[drive].pos, 10*256);    //fread(trackinfo[drive][1],10*256,1,ssdf[drive]);
            ssdf[drive].pos += 10*256;
        }
        else
        {
        	ssdf[drive].pos = track*10*256; //fseek(ssdf[drive],track*10*256,SEEK_SET);
        	int cb = 10*256;
        	cb = min(cb, ssdf[drive].cb-ssdf[drive].pos);
        	LOGI("cb=%d pos=%d drive.cb=%d", cb, ssdf[drive].pos, ssdf[drive].cb);
        	memcpy(trackinfo[drive][0], ssdf[drive].img+ssdf[drive].pos, cb);    //fread(trackinfo[drive][0],10*256,1,ssdf[drive]);
        	ssdf[drive].pos += cb;
        }
}



void ssd_writeback(int drive, int track)
{
    LOGI("ssd_writeback NYI");
	/*
	 * TODO

        if (!ssdf[drive]) return;
        if (dsd[drive])
        {
                fseek(ssdf[drive],track*20*256,SEEK_SET);
                fwrite(trackinfo[drive][0],10*256,1,ssdf[drive]);
                fwrite(trackinfo[drive][1],10*256,1,ssdf[drive]);
        }
        else
        {
                fseek(ssdf[drive],track*10*256,SEEK_SET);
                fwrite(trackinfo[drive][0],10*256,1,ssdf[drive]);
        }
     */
}

void ssd_readsector(int drive, int sector, int track, int side, int density)
{
        ssdsector=sector;
        ssdtrack=track;
        ssdside=side;
        ssddrive=drive;
        LOGI("ssd_readsector  %i %i %i %i\n",drive,side,track,sector);
        
        if (!ssdf[drive].img || (side && !dsd[drive]) || density || track!=ssdtrackc[drive])
        {
                ssdnotfound=500;
                LOGI("ssd_readsector - not found!\n");
                return;
        }
        ssdread=1;
        ssdreadpos=0;
//        printf("GO\n");
}

void ssd_writesector(int drive, int sector, int track, int side, int density)
{
        ssdsector=sector;
        ssdtrack=track;
        ssdside=side;
        ssddrive=drive;
        LOGI("ssd_writesector %i %i %i %i\n",drive,side,track,sector);
        
        if (!ssdf[drive].img || (side && !dsd[drive]) || density || track!=ssdtrackc[drive])
        {
                ssdnotfound=500;
                return;
        }
        ssdwrite=1;
        ssdreadpos=0;
        ssdtime=-1000;
}

void ssd_readaddress(int drive, int track, int side, int density)
{
        ssdtrack=track;
        ssdside=side;
        ssddrive=drive;
//        printf("Read address %i %i %i %i\n",drive,track,side,density);

        if (!ssdf[drive].img || (side && !dsd[drive]) || density || track!=ssdtrackc[drive])
        {
                ssdnotfound=500;
                return;
        }
        ssdrsector=0;
        ssdreadpos=0;
        ssdreadaddr=1;
}

void ssd_format(int drive, int track, int side, int density)
{
        ssdtrack=track;
        ssdside=side;
        ssddrive=drive;

        if (!ssdf[drive].img || (side && !dsd[drive]) || density || track!=ssdtrackc[drive])
        {
                ssdnotfound=500;
                return;
        }
        ssdsector=0;
        ssdreadpos=0;
        ssdformat=1;
}

void ssd_poll()
{
        int c;
        ssdtime++;
        if (ssdtime<16) return;
        ssdtime=0;
        
        if (ssdnotfound)
        {
                ssdnotfound--;
                if (!ssdnotfound)
                {
                        fdcnotfound();
                }
        }
        //LOGI("ssdpoll ssdread=%i ssdf[ssddrive].img=%X",ssdread, ssdf[ssddrive].img);
        if (ssdread && ssdf[ssddrive].img)
        {
        	//LOGI("ssdpoll ssdread ssdreadpos=%d", ssdreadpos);

                fdcdata(trackinfo[ssddrive][ssdside][(ssdsector<<8)+ssdreadpos]);
                ssdreadpos++;
                if (ssdreadpos==256)
                {
                        ssdread=0;
                        fdcfinishread();
                }
        }
        if (ssdwrite && ssdf[ssddrive].img)
        {
                if (writeprot[ssddrive])
                {
                        fdcwriteprotect();
                        ssdwrite=0;
                        return;
                }
//                printf("Write data %i\n",ssdreadpos);
                c=fdcgetdata(ssdreadpos==255);
                if (c==-1)
                {
//                        printf("Data overflow!\n");
//                        exit(-1);
                }
                trackinfo[ssddrive][ssdside][(ssdsector<<8)+ssdreadpos]=c;
                ssdreadpos++;
                if (ssdreadpos==256)
                {
                        ssdwrite=0;
                        fdcfinishread();
                        ssd_writeback(ssddrive,ssdtrack);
                }
        }
        if (ssdreadaddr && ssdf[ssddrive].img)
        {
        	//LOGI("ssdpoll ssdreadaddr");
                switch (ssdreadpos)
                {
                        case 0: fdcdata(ssdtrack); break;
                        case 1: fdcdata(ssdside); break;
                        case 2: fdcdata(ssdrsector); break;
                        case 3: fdcdata(1); break;
                        case 4: fdcdata(0); break;
                        case 5: fdcdata(0); break;
                        case 6:
                        ssdreadaddr=0;
                        fdcfinishread();
                        ssdrsector++;
                        if (ssdrsector==10) ssdrsector=0;
                        break;
                }
                ssdreadpos++;
        }
        if (ssdformat && ssdf[ssddrive].img)
        {
                if (writeprot[ssddrive])
                {
                        fdcwriteprotect();
                        ssdformat=0;
                        return;
                }
                trackinfo[ssddrive][ssdside][(ssdsector<<8)+ssdreadpos]=0;
                ssdreadpos++;
                if (ssdreadpos==256)
                {
                        ssdreadpos=0;
                        ssdsector++;
                        if (ssdsector==10)
                        {
                                ssdformat=0;
                                fdcfinishread();
                                ssd_writeback(ssddrive,ssdtrack);
                        }
                }
        }
}
