/*
	main.c - some JNI entry points for Beebdroid

	Written by Reuben Scratton, based on code by Tom Walker
*/

#include "main.h"
#include "importgl.h"

extern BITMAP* b;
extern uint8_t crtc[];
#define PAL_SIZE 256
extern int pal[256];
int* current_palette;
unsigned short *current_palette16;

int samples;
int autoboot=0;
int joybutton[2];
int ideenable=0;
int resetting=0;
int numSndbufSamples;
short* sndbuf;


extern uint8_t keys[16][16];


jobject  g_obj;
jclass cls;
static JavaVM *gJavaVM = 0;
JNIEnv* env = 0;
jmethodID midAudioCallback;
jmethodID midVideoCallback;

jint JNI_OnLoad(JavaVM *jvm, void *reserved) {
	LOGI("JNI_OnLoad");
	gJavaVM = jvm;
	int status = (*gJavaVM)->GetEnv(gJavaVM, (void **) &env, JNI_VERSION_1_4);
	cls = (*env)->FindClass(env, "com/littlefluffytoys/beebdroid/Beebdroid");
	LOGI("cls=0x%X", cls);
	midAudioCallback = (*env)->GetMethodID(env, cls, "audioCallback", "(II)V");
	LOGI("mid=0x%X", midAudioCallback);
	midVideoCallback = (*env)->GetMethodID(env, cls, "videoCallback", "()V");
	LOGI("mid=0x%X", midAudioCallback);

	importGLInit();

	return JNI_VERSION_1_4;

}
void reset_all() {
	disc_reset();
	ssd_reset();
	reset6502();
	resetsysvia();
	resetuservia();
	reset8271();
}

JNIEXPORT void JNICALL Java_com_littlefluffytoys_beebdroid_Beebdroid_bbcBreak(JNIEnv * env, jobject  obj, jint flags) {
	LOGI("bbcBreak");

	autoboot = 0;
	resetting = 1;
	reset_all();

	the_cpu->pc_triggers[0]=0;
	the_cpu->pc_triggers[1]=0;
	the_cpu->pc_triggers[2]=0;
	the_cpu->pc_triggers[3]=0;

}


JNIEXPORT void JNICALL Java_com_littlefluffytoys_beebdroid_Beebdroid_bbcLoadDisc(JNIEnv * env, jobject  obj, jobject directBuffer, jint do_autoboot)
{
	LOGI("bbcLoadDisc");

	unsigned int size = (*env)->GetDirectBufferCapacity(env, directBuffer);
	unsigned char* disc = (*env)->GetDirectBufferAddress(env, directBuffer);

	loaddisc(0, 0, disc, size);

	if (do_autoboot) {
		autoboot = 150;
		resetting = 1;
		resetsysvia();
	}
}

JNIEXPORT void JNICALL Java_com_littlefluffytoys_beebdroid_Beebdroid_bbcSetTriggers(JNIEnv * env, jobject  obj, jshortArray pc_triggers)
{
	LOGI("bbcSetTriggers");

	unsigned int c = (*env)->GetArrayLength(env, pc_triggers);
	unsigned short* tmp = (*env)->GetShortArrayElements(env, pc_triggers, 0);
	the_cpu->pc_triggers[0]=0;
	the_cpu->pc_triggers[1]=0;
	the_cpu->pc_triggers[2]=0;
	the_cpu->pc_triggers[3]=0;
	if (c>0) the_cpu->pc_triggers[0]=tmp[0];
	if (c>1) the_cpu->pc_triggers[1]=tmp[1];
	if (c>2) the_cpu->pc_triggers[2]=tmp[2];
	if (c>3) the_cpu->pc_triggers[3]=tmp[3];
}



JNIEXPORT void JNICALL Java_com_littlefluffytoys_beebdroid_Beebdroid_bbcExit(JNIEnv * env, jobject  obj) {
	//moncleanup();
}



JNIEXPORT void JNICALL Java_com_littlefluffytoys_beebdroid_Beebdroid_bbcInit(JNIEnv * env, jobject  obj,
		jobject amem, jobject aroms, jbyteArray aaudiobuff, jint firstTime) {

	LOGI("bbcInit");
	autoboot = 0;

	// Profiling
	//monstartup("bbcmicro.so");

	// Get reference to the Java Beebdroid object (needed for callbacks)
	g_obj = (*env)->NewGlobalRef(env, obj);

	// Get pointers to memory and ROMs arrays, allocated on the Java side.
	the_cpu->mem = (*env)->GetDirectBufferAddress(env, amem);
	roms = (*env)->GetDirectBufferAddress(env, aroms);

	// Get sound buffer details
	numSndbufSamples = (*env)->GetArrayLength(env, aaudiobuff);
	sndbuf = (short*) (*env)->GetByteArrayElements(env, aaudiobuff, 0);


	// First-time init
	if (firstTime) {
		LOGI("calling initvideo()");
		initvideo();
		makemode7chars();

		reset_all();

		initsound();
		initadc();
	}

	LOGI("exiting initbbc()");
}



JNIEXPORT jint JNICALL Java_com_littlefluffytoys_beebdroid_Beebdroid_bbcRun(JNIEnv * env, jobject  obj)
{

	if (autoboot)
		autoboot--;

	exec6502();

	checkkeys();

	// Break!
	if (resetting) {
		LOGI("resetting... ");
		reset6502();
		reset8271();
		resetting = 0;
	}


	return the_cpu->pc_trigger_hit;
}




extern void dump_pages();

JNIEXPORT void JNICALL Java_com_littlefluffytoys_beebdroid_Beebdroid_bbcKeyEvent(JNIEnv * env, jobject  obj, jint vkey, jint flags, jint down) {
	//if (vkey==0xaa) return;
	if (vkey&0x100) {
		vkey &= 0xff;
		flags = 1;
	}
	if (vkey&0x200) {
		vkey &= 0xff;
		flags = 0;
	}
	int col=vkey&15;
	int row=(vkey>>4)&15;

	if (down && vkey==0x37) {
		dump_pages();
	}

	// Press / unpress SHIFT
	keys[0][0] = flags ? down : 0x00;
	// Press / unpress the key
	keys[col][row] = down? 1:0;
	//LOGI("Key event %d,%d = %d", col, row, down);
}







extern uint8_t* load_cpu(uint8_t* p);
extern uint8_t* save_cpu(uint8_t* p);
extern uint8_t* load_video(uint8_t* p);
extern uint8_t* save_video(uint8_t* p);
extern uint8_t* load_sysvia(uint8_t* p);
extern uint8_t* save_sysvia(uint8_t* p);

JNIEXPORT int JNICALL Java_com_littlefluffytoys_beebdroid_Beebdroid_bbcSerialize(JNIEnv * env, jobject  obj, jbyteArray abuff) {

	LOGI("bbcSerialize");
	uint8_t* buff = (uint8_t*) (*env)->GetByteArrayElements(env, abuff, 0);
	uint8_t* buff_orig = buff;

	// RAM
	memcpy(buff, the_cpu->mem, 65536);
	buff += 65536;

	// Save state
	buff = save_cpu(buff);
	buff = save_video(buff);
	buff = save_sysvia(buff);


	return (buff - buff_orig);
}

JNIEXPORT void JNICALL Java_com_littlefluffytoys_beebdroid_Beebdroid_bbcDeserialize(JNIEnv * env, jobject  obj, jbyteArray abuff) {

	LOGI("bbcDeserialize");
	uint8_t* buff = (uint8_t*) (*env)->GetByteArrayElements(env, abuff, 0);

	// RAM
	memcpy(the_cpu->mem, buff, 65536);
	buff += 65536;

	// Video controller registers
	buff = load_cpu(buff);
	buff = load_video(buff);
	buff = load_sysvia(buff);



}

void givealbuffer(int16_t *buf, int pos, int cb) {
	//LOGI("givealbuffer! %02X %02X %02X %02X %02X %02X %02X %02X", buf[0], buf[1], buf[2], buf[3], buf[4], buf[5], buf[6], buf[7]);
	(*env)->CallVoidMethod(env, g_obj, midAudioCallback, pos, cb);
}




/*
 * Clears the bitmap to the specified color
 */
void clear_to_color(BITMAP *bitmap, int color) {
	LOGI("clear_to_color");
	int x,y;
	unsigned char* pixels = bitmap->pixels;
	for (y=0 ; y<bitmap->height ; y++) {
		for (x=0 ; x<bitmap->width ; x++) {
			pixels[x] = color;
		}
		pixels += bitmap->stride;
	}
}

/*
 * Sets the entire palette of 256 colors. You should provide an array of 256 RGB structures.
 * Unlike set_color(), there is no need to call vsync() before this function
 */

unsigned short RGB_TO_565(unsigned int rgb) {
	return (RED(rgb)>>3)<<11 | ((GREEN(rgb)>>2)<<5) | ((BLUE(rgb)>>3));
}

void set_palette(int* p) {
	LOGI("set_palette");
	int i;
	current_palette16 = (unsigned short*)malloc(2*256);
	for (i=0 ; i<256 ; i++) {
		current_palette16[i] = RGB_TO_565(p[i]);
	}
}


float vertexes[] = {
		0,0,0,
		1,0,0,
		0,1,0,
		1,1,0
};

extern int s_firstx, s_lastx, s_firsty, s_lasty;


JNIEXPORT jint JNICALL Java_com_littlefluffytoys_beebdroid_Beebdroid_bbcGetThumbnail(JNIEnv * env, jobject  obj, jobject jbitmap)
{
	AndroidBitmapInfo info;
	void* pixels;
	int ret;

    if ((ret = AndroidBitmap_getInfo(env, jbitmap, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return;
    }


    if ((ret = AndroidBitmap_lockPixels(env, jbitmap, &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

	uint8_t* pixelsSrc = (uint8_t*)(b->pixels + s_firsty * b->stride);
	uint8_t* pixelsDst = (uint8_t*)(pixels   + 0 * info.stride);
	int cx = s_lastx - s_firstx;
	int cy = s_lasty - s_firsty;
	int y;
	for (y=0 ; y<cy ; y++) {
		memcpy(pixelsDst, pixelsSrc + s_firstx*2, cx*2);
		pixelsSrc += b->stride;
		pixelsDst += info.stride;
	}
    AndroidBitmap_unlockPixels(env, jbitmap);
    return 0;
}

/*

JNIEXPORT jint JNICALL Java_com_littlefluffytoys_beebdroid_Beebdroid_bbcRender(JNIEnv * env, jobject  obj, jobject jbitmap)
{
	AndroidBitmapInfo info;
    void*              pixels;
    int                ret;

    if (b == NULL) {
    	return;
    }

    if ((ret = AndroidBitmap_getInfo(env, jbitmap, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return;
    }


    if ((ret = AndroidBitmap_lockPixels(env, jbitmap, &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }


	if (screenblitinfo.source_y<0) {
		screenblitinfo.height += screenblitinfo.source_y;
		screenblitinfo.dest_y -= screenblitinfo.source_y;
		screenblitinfo.source_y=0;
	}
	if (screenblitinfo.source_y<64000) {
		screenblitinfo.pixels = pixels;
		screenblitinfo.stride = info.stride;
		screenblitinfo.source_y >>= 1;
		register unsigned char*  pixelsSrc = (unsigned char*)(b->pixels + screenblitinfo.source_y * b->stride);
		register unsigned short* pixelsDst = (unsigned short*)(pixels   + screenblitinfo.dest_y   * info.stride);
		pixelsDst += screenblitinfo.dest_x;
		register int x,y;
		register unsigned short* pal = current_palette16;
		for (y=0 ; y<screenblitinfo.height>>1 ; y++) {
			for (x=0 ; x<screenblitinfo.width; x+=4) {
				// Read 4 pixel values at once (source bitmap is 8-bit)
				register uint32_t pixval = *(uint32_t*)(pixelsSrc+xoff+x);
				// Write 2 output pixel values at a time
				*(unsigned int*)(pixelsDst + x) = (pal[(pixval&0xff00u)>>8]<<16) | pal[pixval&0xff];
				*(unsigned int*)(pixelsDst + x + 2) = (pal[pixval>>24]<<16) | pal[(pixval&0xff0000u)>>16];
			}
			pixelsSrc += b->stride;
			pixelsDst += info.stride/2;
		}
	}


    AndroidBitmap_unlockPixels(env, jbitmap);
    return (((crtc[9]+1)*crtc[4]) <<16) | (crtc[1]*8);
}*/

BITMAP *create_bitmap(int width, int height) {
//	LOGI("create_bitmap %d x %d on 0x%X", width, height, g_obj);
	BITMAP* bmp = (BITMAP*)malloc(sizeof(BITMAP));
	bmp->width=width;
	bmp->height=height;
	bmp->bpp = 16;
	bmp->stride = width * (16/8);
	bmp->pixels = (unsigned char*) malloc(bmp->stride*height);
	return bmp;
}



float textureCoords[] = {
		0,.5f,
		1,.5f,
		0,0,
		1,0
};
short indexes[] = {
		0,1,2,3
};

int tex;
int beebview_width;
int beebview_height;

//
// bbcInitGl
//
JNIEXPORT jint JNICALL Java_com_littlefluffytoys_beebdroid_Beebdroid_bbcInitGl(JNIEnv * env, jobject  obj, jint width, jint height)
{
	beebview_width = width;
	beebview_height = height;

 	// Enable texture unit
 	glEnable(GL_TEXTURE_2D);
 	glEnableClientState(GL_VERTEX_ARRAY);
 	glEnableClientState(GL_TEXTURE_COORD_ARRAY);

 	glEnable(GL_BLEND);
 	glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

 	// Get a texture ID
	int textures[] = {1};
	glGenTextures(1, textures);
	tex = textures[0];

	// Set up the texture
	glBindTexture(GL_TEXTURE_2D, tex);
	glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

	// Assign the bitmap pixels to the texture
	glTexImage2D(GL_TEXTURE_2D,
		0,
		GL_RGB,
		1024,
		512,
		0,
		GL_RGB,
		GL_UNSIGNED_SHORT_5_6_5,
		b->pixels);
}



void blit_to_screen(int source_x, int source_y, int width, int height)
{

	glViewport(0, 0, beebview_width, beebview_height);
	glMatrixMode(GL_PROJECTION);
	glLoadIdentity();
	glOrthof(0, 1, 0, 1, -1, 1);
	glMatrixMode(GL_MODELVIEW);

    glBindTexture(GL_TEXTURE_2D, tex);

    //LOGI("source_y=%d, height=%d", source_y, height);
    textureCoords[0] = textureCoords[4] = (float)source_x / (float)1024.0f;
    textureCoords[2] = textureCoords[6] = (float)(source_x+width) / (float)1024.0f;
    textureCoords[5] = textureCoords[7] = (float)source_y / (float)512.0f;
    textureCoords[1] = textureCoords[3] = (float)(source_y+height) / (float)512.0f;

    // Update the texture with the updated bitmap pixels
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0,0,
            	             1024,320, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, b->pixels);
    /*glTexImage2D(GL_TEXTURE_2D,
    		0,
    		GL_RGB,
    		1024,
    		512,
    		0,
    		GL_RGB,
    		GL_UNSIGNED_SHORT_5_6_5,
    		b->pixels);
*/
	glVertexPointer(3, GL_FLOAT, 0, vertexes);
	glTexCoordPointer(2, GL_FLOAT, 0, textureCoords);
	glDrawElements(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_SHORT, indexes);

	// Swap buffers on the java side (also updates FPS display)
	(*env)->CallVoidMethod(env, g_obj, midVideoCallback);

}




/* generate_332_palette:
 *  Used when loading a truecolor image into an 8 bit bitmap, to generate
 *  a 3.3.2 RGB palette.
 */
#define MAKE_RGB(r,g,b) ((b<<16)|(g<<8)|r)

void generate_332_palette(int* pal)
{
   int c;

   for (c=0; c<PAL_SIZE; c++) {
	   unsigned char r = ((c>>5)&7) * 255/7;
	   unsigned char g = ((c>>2)&7) * 255/7;
	   unsigned char b = (c&3) * 255/3;
	   pal[c] = MAKE_RGB(r,g,b);
   }
}


/*void updatewindowsize(int x, int y)
{
	x=(x+3)&~3; y=(y+3)&~3;
	if (x<128) x=128;
	if (y<64)  y=64;
	if (windx!=x || windy!=y) {
		windx=winsizex=x; windy=winsizey=y;
		set_palette(pal);
	}
}*/




int page_hits[256];
int inpage_hits[256];
int inpage = 0x32;

void chk_triggers(M6502* cpu, uint16_t pc) {
	page_hits[pc>>8]++;

	if (pc>>8 == inpage) {
		inpage_hits[pc&255]++;
	}
}

void dump_pages() {
	int i;
	LOGI("Page dump:");
	for (i=0 ; i<128 ; i++) {
		if (page_hits[i] > 0) {
			LOGI("Page 0x%04X hit %d times", i*256, page_hits[i]);
			page_hits[i] = 0;
		}
	}
	LOGI("In-page dump:");
	for (i=0 ; i<256 ; i++) {
		if (inpage_hits[i] > 0) {
			LOGI("0x%02X%02X hit %d times", inpage, i, inpage_hits[i]);
			inpage_hits[i] = 0;
		}
	}
}

void closebbc()
{
        closedisc(0);
        closedisc(1);

}

