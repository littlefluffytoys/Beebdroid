@ 6502asm.S
@
@ ARM assembly version of (some of) 6502.c. Not brilliantly optimized yet, but a million times quicker than the original C.
@
@ Written by Reuben Scratton for Little Fluffy Toys Ltd before, after, and almost during getting married on June 25th 2011.
@


.text
.global exec6502

@ This implementation keeps 6502 state entirely in ARM registers during exec6502(), in order to reduce memory accesses.
@ We haven't yet exploited the performance gains because we are still calling do_poll() after every
@ instruction (i.e. pushing 6502 state on the stack with every emulated instruction). A goal for future development
@ is to call do_poll() far less frequently without interfering with the accuracy of the emulation.
@
@  r4 = cpu*
@  r5 = mem
@  r6 = PC
@  r7 = A
@  r8 = X
@  r9 = Y
@ r10 = S
@ r11 = P
@ r12 = cpu->cycles

// branch without labels: count number of lines to skip over, add 1, multiply by 4. So to skip 2 lines, "b .+12"
.macro PUSHWORD reg16, scrapreg
	add \scrapreg, %r5, #0x100  	@ scrapreg = mem+0x100, i.e. bottom of stack space
	sub %r10, %r10, #1
	and %r10, %r10, #255
	strh \reg16, [\scrapreg, %r10]
	sub %r10, %r10, #1
	and %r10, %r10, #255
.endm
.macro PULLWORD reg16
	add %r2, %r5, #0x100  	@ scrapreg = mem+0x100, i.e. bottom of stack space
	add %r10, %r10, #1
	and %r10, %r10, #255
	ldrh \reg16, [%r2, %r10]
	add %r10, %r10, #1
	and %r10, %r10, #255
.endm

.macro UPDATE_TAKEINT 	@ cpu->takeint=(cpu->interrupt && !(cpu->p & FLAG_I));
	mov  %r1, #0
	ldr  %r2, [%r4, #12]	@r12 = cpu->interrupt
	cmp  %r2, #0
	beq  .+12
	tstne  %r11, #4  		@ test the FLAG_I
	moveq %r1, #1
	str %r1, [%r4, #20]   	@cpu->take_int = r1
.endm

exec6502:
	push {%r4-%r12,lr}

	@ Keep CPU* in safe register
	ldr  %r4, =acpu

    @ update cpu->cycles. Note the constant value 40000 cannot be encoded and has to be ldr'd indirectly.
    ldr  %r0,[%r4, #24]
    ldr  %r1, =40000
    add  %r0, %r0, %r1
    str  %r0,[%r4, #24]

    @ load 6502 CPU state into ARM registers
    ldr  %r5, [%r4] 		@ r5  = mem
    ldrh %r6, [%r4, #4] 	@ r6  = PC
    ldrb %r7, [%r4, #6] 	@ r7  = A
    ldrb %r8, [%r4, #7] 	@ r8  = X
    ldrb %r9, [%r4, #8] 	@ r9  = Y
    ldrb %r10,[%r4, #9] 	@ r10 = S
    ldrb %r11,[%r4, #10] 	@ r11 = P

    @loop
loop:

	@ Trigger spotting... log PCs (NB: ALWAYS REMOVE FROM RELEASE CODE! ITS ONLY FOR FINDING GOOD PLACES FOR TRIGGERS)
	//mov %r0, %r4
	//mov %r1, %r6
	//bl chk_triggers

	@ PC triggers - if PC hits a known preset, update CPU state with it so Java layer can act appropriately
	mov %r1, #1
	ldrh %r0,[%r4, #30]
	cmp %r0, %r6
	streqh %r1,[%r4, #28]
	mov %r1, #2
	ldrh %r0,[%r4, #32]
	cmp %r0, %r6
	streqh %r1,[%r4, #28]

	@ Get the opcode into r0 and update cpu->PC
	ldrb %r0,[%r5,%r6]
	add  %r6, %r6, #1

	/*cmp %r0, #0x68
	blt .+12
	cmp %r0, #0x6a
	blt use_c_fn*/
	//cmp %r0, #0x69
	//beq use_c_fn

//*/
	@ Get the ASM function. If this doesnt exist, fall back to C version.
	ldr  %r1, =fns_asm
	ldr  %r2, [%r1, %r0, lsl #2]
	cmp  %r2, #0
	beq  use_c_fn

	@ Call assembly function
	blx %r2
	b done_call

	@ Get the C function pointer and call it, updating and restoring the in-memory 6502 state before and after the call.
use_c_fn:
	ldr  %r1, =fns
	ldr  %r2, [%r1, %r0, lsl #2]
    strh %r6, [%r4, #4] 	@  r6 = PC
    strb %r7, [%r4, #6] 	@  r7 = A
    strb %r8, [%r4, #7] 	@  r8 = X
    strb %r9, [%r4, #8] 	@  r9 = Y
    strb %r10,[%r4, #9] 	@ r10 = S
    strb %r11,[%r4, #10] 	@ r11 = P
	mov  %r0, %r4  	@ Sole parameter for C opcode functions is r0 = CPU*
	blx %r2
    ldrh %r6, [%r4, #4] 	@  r6 = PC
    ldrb %r7, [%r4, #6] 	@  r7 = A
    ldrb %r8, [%r4, #7] 	@  r8 = X
    ldrb %r9, [%r4, #8] 	@  r9 = Y
    ldrb %r10,[%r4, #9] 	@ r10 = S
    ldrb %r11,[%r4, #10] 	@ r11 = P

done_call:
	tst  %r0, #0x80000000
	bne  already_updated_interrupts
	@ cpu->takeint=(cpu->interrupt && !(cpu->p & FLAG_I));
	UPDATE_TAKEINT
	b done_interrupts
already_updated_interrupts:
	mvn  %r0, %r0
	add  %r0, %r0, #1
done_interrupts:

	@ Update interrupts
	ldr %r1, [%r4, #16]  @ r1 = cpu->nmi
	cmp %r1, #0
	beq no_nmi
	mov %r1, #0
	str %r1, [%r4, #16]  @ cpu->nmi = 0
	ldr %r3, =0xfffa
	b do_interrupt
no_nmi:
	ldr %r1, [%r4, #20]  @ r1 = cpu->take_int
	cmp %r1, #0
	beq no_interrupt
	mov %r1, #0
	str %r1, [%r4, #20]  @ cpu->take_int = 0
	ldr %r3, =0xfffe
do_interrupt:
	PUSHWORD %r6, %r2			@ push(cpu->pc)
	orr %r1,  %r11, #0x20   	@ uint8_t temp = cpu->p | 0x20;
	strb %r1, [%r2, %r10] 		@ push(temp);
	sub %r10, %r10, #1
	and %r10, %r10, #255
	ldrh %r6, [%r5, %r3] 		@ cpu->pc=*(uint16_t*)&(cpu->mem[0xfffe]);
	orr %r11, #4 			 	@ cpu->p |= FLAG_I;
	strh %r6, [%r4, #4]			@ Remove when C-only
	strb %r10, [%r4, #9]    	@ Remove when C-only
	strb %r11, [%r4, #10]		@ Remove when C-only
	add %r0, %r0, #7		@	 c += 7;
no_interrupt:
	ldr %r1, [%r4, #12]  @ r1 = cpu->interrupt
	bic %r1, #128
	str %r1, [%r4, #12]

	@ Update CPU->cycles
	mov %r1, %r0
	ldr  %r0,[%r4, #24]
	subs  %r0, %r1
	str  %r0,[%r4, #24]
	//bcs do_poll_now

	@ Update intermediate cycle counter
/*	ldr  %r0,[%r4, #38]
	add  %r0, %r1
	str  %r0,[%r4, #38]
	cmp %r0, #32
	blt no_poll_yet
	sub %r0, #32
	str  %r0,[%r4, #38]
do_poll_now:*/
	mov %r0, %r4
 	bl do_poll
no_poll_yet:

	@ Loop back until cpu->cycles <=0
    ldr  %r0,[%r4, #24]
	cmp  %r0, #0
	bgt loop

	@ Update cpu fields, restore regs and exit
    strh %r6, [%r4, #4] 	@  r6 = PC
    strb %r7, [%r4, #6] 	@  r7 = A
    strb %r8, [%r4, #7] 	@  r8 = X
    strb %r9, [%r4, #8] 	@  r9 = Y
    strb %r10,[%r4, #9] 	@ r10 = S
    strb %r11,[%r4, #10] 	@ r11 = P
	pop {%r4-%r12,lr}
    bx   lr


 .ltorg


// branch without labels: count number of lines to skip over, add 1, multiply by 4. So to skip 2 lines, "b .+12"

//#define readmem(x)  ((x<0xfe00) ? cpu->mem[x] : readmem_ex(x))
.macro READBYTED a, b
	ldrb \a, [%r5, \b]
.endm
.macro READBYTE a, b
	cmp \b, #0xfe00
	bge .+12
	ldrb \a, [%r5, \b]
	b .+24
	mov %r0, \b
	push {lr}
	bl readmem_ex
	pop {lr}
	movs \a, %r0
.endm
.macro READSIGNEDBYTE a, b
	cmp \b, #0xfe00
	bge .+12
	ldrsb \a, [%r5, \b]
	b .+24
	mov %r0, \b
	push {lr}
	bl readmem_ex
	pop {lr}
	movs \a, %r0
.endm
.macro READBYTE_PC a
	READBYTE \a, %r6
	add %r6, %r6, #1
.endm
.macro WRITEBYTED val, addr
	strb \val, [%r5, \addr]
.endm
.macro WRITEBYTE val, addr
	cmp \addr, #0x8000
	bge .+12
	strb \val, [%r5, \addr]
	b .+24
	mov %r0, \addr
	mov %r1, \val
	push {lr}
	bl writemem_ex
	pop {lr}
.endm
.macro RETURN clocks
	mov %r0, \clocks
	bx lr
.endm
//#define readword(x) ((x<0xfe00) ? (*((uint16_t*)&(cpu->mem[x]))) : (readmem_ex(x) | (readmem_ex(x+1)<<8)))
.macro READWORD dst, src
	cmp \src, #0xfe00
	bge .+12
	ldrh \dst, [%r5, \src]
	b .+24
	mov %r0, \src
	push {lr}
	bl readword_ex
	pop {lr}
	movs \dst, %r0
.endm
.macro READWORD_PC a
	READWORD \a, %r6
	add %r6, %r6, #2
.endm

// #define setzn(v) if(v) cpu->p &=~FLAG_Z; else cpu->p|=FLAG_Z;  if ((v)&0x80) cpu->p|=FLAG_N; else cpu->p&=~FLAG_N;
.macro SETZN a
	bic %r11, #0x82
	ands \a, #255
	orreq %r11, #2
	tst \a, #0x80
	orrne %r11, #0x80
.endm

.macro PUSHBYTE reg8
	add %r2, %r5, #0x100  	@ scrapreg = mem+0x100, i.e. bottom of stack space
	strb \reg8, [%r2, %r10]
	sub %r10, %r10, #1
	and %r10, %r10, #255
.endm
.macro PULLBYTE reg8
	add %r2, %r5, #0x100  	@ scrapreg = mem+0x100, i.e. bottom of stack space
	add %r10, %r10, #1
	and %r10, %r10, #255
	ldrb \reg8, [%r2, %r10]
.endm

.macro START_ZP
	READBYTE_PC %r0			@ uint16_t addr=readmem(cpu->pc); cpu->pc++;
	READBYTED %r1, %r0		@ uint8_t temp=readmem(addr);
.endm

.macro START_ZP_X
	READBYTE_PC %r0			@ uint16_t addr=readmem(cpu->pc); cpu->pc++;
	add %r0, %r0, %r8
	and %r0, %r0, #255
	READBYTED %r1, %r0		@ uint8_t temp=readmem(addr);
.endm

.macro START_ABS
	READWORD_PC %r0			@ uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	READBYTE %r1, %r0		@ uint8_t temp=readmem(addr);
.endm



.macro START_ABS_O reg
	READWORD_PC %r0			@ uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	add %r0, \reg
	READBYTE %r1, %r0		@ uint8_t temp=readmem(addr);
.endm
.macro START_ABS_X
	START_ABS_O %r8
.endm
.macro START_ABS_Y
	START_ABS_O %r9
.endm


// For instructions of the form OPCODE (,x)
.macro START_INDZX
	READBYTE_PC %r1			@ register uint8_t temp=readmem(cpu->pc); pc++;
	add %r1, %r8			@ temp += cpu->x;
	and %r1, #255
	READWORD %r0, %r1		@ register uint16_t addr=readword(temp);
.endm

// For instructions of the form OPCODE (),y
.macro START_INDZY
	READBYTE_PC %r1			@ register uint8_t temp=readmem(cpu->pc); pc++;
	READWORD %r0, %r1		@ register uint16_t addr=readword(temp);
	add %r0, %r9			@ addr += cpu->y;
	READBYTE %r1, %r0		@ temp = readmem(addr);
.endm




/*************************************/
/**  OPCODES IN ALPHABETICAL ORDER  **
/*************************************/


/*******
 * ADC *
 *******/

.macro BODY_ADC
	tst %r11, #8		@ if (cpu->p & FLAG_D) do BCD arithmetic in a C function
	bne .+80
	add %r2, %r7, %r1   	@ r2 = (cpu->a+temp+((cpu->p & FLAG_C)?1:0));
	tst %r11, #1
	addne %r2, #1
	bic %r11, #0x41     	@ clear C and V flags
	tst %r2, #256
	orrne %r11, #1			@ set FLAG_C
	eor %r0, %r7, %r1		@ r0 = cpu->a ^ temp
	lsls %r0, #24
	bmi .+16
	eor %r0, %r7, %r2		@ r1 = cpu->a ^ result_of_addition
	lsls %r0, #24
	orrmi %r11, #0x40		@ set FLAG_V
	and %r7, %r2, #255		@ cpu->a = result_of_addition & 255
	SETZN %r7				@ setzn(cpu->a);   <-- 5 instructions
	b .+36
	push {lr}
	strb %r7,  [%r4, #6]	// update A
	strb %r11, [%r4, #10]   // update P
	mov %r0, %r4			// r0=cpu*, r1=imm
	bl adc_bcd
	pop {lr}
	ldrb %r7,  [%r4, #6]	// update A
	ldrb %r11, [%r4, #10]   // update P
.endm

// 0x69: ADC imm
opasm_adc_imm:
	READBYTE_PC %r1		@ register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	BODY_ADC
	RETURN #2

// 0x65: ADC zp
opasm_adc_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_ADC
	RETURN #3				@ return 3;

// 0x75: ADC zp,x
opasm_adc_zp_x:
	START_ZP_X				@ r0=zp address, r1=byte from zp address
	BODY_ADC
	RETURN #3				@ return 3;

// 0x6D: ADC abs
opasm_adc_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_ADC
	RETURN #4				@ return 4

// 0x79: ADC abs,y
opasm_adc_abs_y:
	START_ABS_Y				@ r0=address, r1=byte from address
	BODY_ADC
	RETURN #4				@ return 4

// 0x7D: ADC abs,x
opasm_adc_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_ADC
	RETURN #4				@ return 4

// 0x61: ADC (,x)
opasm_adc_indzx:
	START_INDZX
	READBYTE %r1, %r0		@ temp = readmem(addr);
	BODY_ADC
	RETURN #6				@ return 6

// 0x71: ADC (),y
opasm_adc_indzy:
	START_INDZY
	BODY_ADC
	RETURN #5				@ return 5


/*******
 * AND *
 *******/
.macro BODY_AND
	and %r7, %r7, %r1		@ cpu->a &= temp;
	SETZN %r7				@ setzn(cpu->a);
.endm

// 0x21: AND (,x)
opasm_and_indzx:
	START_INDZX
	READBYTE %r1, %r0		@ temp = readmem(addr);
	BODY_AND
	RETURN #6				@ return 6

// 0x31: AND (),y
opasm_and_indzy:
	START_INDZY
	BODY_AND
	RETURN #5				@ return 5

// 0x29: AND imm
opasm_and_imm:
	READBYTE_PC %r1			@ r1 = imm
	BODY_AND
	RETURN #3				@ return 3

// 0x25: AND zp
opasm_and_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_AND
	RETURN #3				@ return 3

// 0x35: AND zp,x
opasm_and_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	BODY_AND
	RETURN #3				@ return 3

// 0x2D: AND abs
opasm_and_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_AND
	RETURN #4				@ return 4

// 0x39: AND abs,y
opasm_and_abs_y:
	START_ABS_Y				@ r0=address, r1=byte from address
	BODY_AND
	RETURN #4				@ return 4

// 0x3D: AND abs,x
opasm_and_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_AND
	RETURN #4				@ return 4


/*******
 * ASL *
 *******/

.macro BODY_ASL reg
	lsl \reg, #1			@ temp<<=1;
	bic %r11, #1			@ clear FLAG_C
	tst \reg, #0x100
	orrne %r11, #1			@ set FLAG_C if bit 7 carried over
	and \reg, #255
	SETZN \reg				@ setzn(temp);
.endm

opasm_asl_a:
	BODY_ASL %r7
	RETURN #2

// 0x06: ASL zp
opasm_asl_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_ASL %r1
	WRITEBYTED %r1, %r0
	RETURN #5				@ return 5

// 0x16: ASL zp,x
opasm_asl_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	BODY_ASL %r1
	WRITEBYTED %r1, %r0
	RETURN #5				@ return 5

// 0x0E: ASL abs
opasm_asl_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_ASL %r1
	WRITEBYTE %r1, %r0
	RETURN #6				@ return 6;

// 0x1E: ASL abs,x
opasm_asl_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_ASL %r1
	WRITEBYTE %r1, %r0
	RETURN #7				@ return 7;


/*************************************************
 * BNE / BEQ / BPL / BMI / BVC / BVS / BCC / BCS *
 *************************************************/

.macro BRANCH flag, testop
	READSIGNEDBYTE %r1, %r6		@ register int8_t offset=(int8_t)readmem(cpu->pc);
	add %r6, #1 				@ pc++;
	mov %r0, #2					@ register uint8_t temp=2;
	tst %r11, \flag				@ if (!(cpu->p & FLAG_Z)) {
	\testop .+12
	add %r0, %r0, #1			@     temp++;
	add %r6, %r6, %r1			@     cpu->pc+=offset;
	bx lr						@ }
.endm

opasm_bne:
	BRANCH #2, bne
opasm_beq:
	BRANCH #2, beq
opasm_bpl:
	BRANCH #0x80, bne
opasm_bmi:
	BRANCH #0x80, beq
opasm_bvc:
	BRANCH #0x40, bne
opasm_bvs:
	BRANCH #0x40, beq
opasm_bcc:
	BRANCH #1, bne
opasm_bcs:
	BRANCH #1, beq



/*******
 * BIT *
 *******/

.macro BODY_BIT
	bic %r11, #0xc2			@ clear Z,V,N
	tst %r1, #0x40			@ SET_FLAG(FLAG_V, temp&0x40);
	orrne %r11, #0x40
	tst %r1, #0x80			@ SET_FLAG(FLAG_N, temp&0x80);
	orrne %r11, #0x80
	and %r1, %r7, %r1		@ temp = cpu->a&temp
	cmp %r1, #0				@ SET_FLAG(FLAG_Z, !temp);
	orreq %r11, #2
.endm

// 0x24: BIT zp
opasm_bit_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_BIT
	RETURN #3				@ return 3

// 0x2C: BIT abs
opasm_bit_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_BIT
	RETURN #4				@ return 4


/*************************
 * CLC / CLD / CLV / CLI *
 *************************/
opasm_clc:
	bic %r11, #1 			@ cpu->p &= ~FLAG_C;
	RETURN #2
opasm_cld:
	bic %r11, #8 			@ cpu->p &= ~FLAG_D;
	RETURN #2
opasm_clv:
	bic %r11, #0x40 		@ cpu->p &= ~FLAG_V;
	RETURN #2
opasm_cli:
	UPDATE_TAKEINT			@ cpu->takeint=(cpu->interrupt && !(cpu->p & FLAG_I));
	bic %r11, #4			@ cpu->p &= ~FLAG_I;
	RETURN #-2				@ return -2;


/*******
 * CMP *
 *******/
.macro BODY_CMP reg
	bic %r11, #1			@ clear FLAG_C
	subs %r1, \reg, %r1		@ temp = cpu->a - temp
	orrcs %r11, #1			@ set FLAG_C if carried
	SETZN %r1				@ setzn(temp);
.endm

// 0xC9: CMP imm
opasm_cmp_imm:
	READBYTE_PC %r1		@ register uint8_t temp=readmem(cpu->pc); cpu->pc++;
    BODY_CMP %r7
	RETURN #2			@ return 2;

// 0xC5: CMP zp
// 0xE4: CPX zp
// 0xC4: CPY zp
.macro CMP_ZP reg
	START_ZP				@ r0=zp address, r1=byte from zp address
	bic %r11, #1			@ clear FLAG_C
	subs %r1, \reg, %r1		@ temp = cpu->a - temp
	orrcs %r11, #1			@ set FLAG_C if carried
	SETZN %r1				@ setzn(temp);
    RETURN #3				@ return 3;
.endm
opasm_cmp_zp:
	CMP_ZP %r7
opasm_cpx_zp:
	CMP_ZP %r8
opasm_cpy_zp:
	CMP_ZP %r9

// 0xD5: CMP zp,x
opasm_cmp_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	BODY_CMP %r7
    RETURN #3				@ return 3;

// 0xCD: CMP abs
opasm_cmp_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_CMP %r7
    RETURN #4				@ return 4;

// 0xD9: CMP abs,y
opasm_cmp_abs_y:
	START_ABS_Y				@ r0=address, r1=byte from address
	BODY_CMP %r7
    RETURN #4				@ return 4;

// 0xDD: CMP abs,x
opasm_cmp_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_CMP %r7
    RETURN #4				@ return 4;

// 0xC1: CMP (,x)
opasm_cmp_indzx:
	START_INDZX				@ r0=address, r1=byte from address
	READBYTE %r1, %r0		@ temp = readmem(addr);
	BODY_CMP %r7
    RETURN #6				@ return 6;

// 0xD1: CMP (),y
opasm_cmp_indzy:
	START_INDZY				@ r0=address, r1=byte from address
	BODY_CMP %r7
    RETURN #6				@ return 6;


/*******
 * CPX *
 *******/

.macro BODY_CPX
	sub %r0, %r8, %r1
	SETZN %r0			@ setzn(cpu->y - temp);
    bic %r11, #1		@ SET_FLAG(FLAG_C, cpu->y>=temp);
 	cmp %r8, %r1
 	orrge %r11, #1
.endm

// 0xE0: CPX imm
opasm_cpx_imm:
	READBYTE_PC %r1		@ register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	BODY_CPX
	RETURN #2			@ return 2;

// 0xEC: CPX abs
opasm_cpx_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_CPX
	RETURN #4				@ return 4


/*******
 * CPY *
 *******/

.macro BODY_CPY
	sub %r0, %r9, %r1
	SETZN %r0			@ setzn(cpu->y - temp);
    bic %r11, #1		@ SET_FLAG(FLAG_C, cpu->y>=temp);
 	cmp %r9, %r1
 	orrge %r11, #1
.endm

// 0xC0: CPY imm
opasm_cpy_imm:
	READBYTE_PC %r1		@ register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	BODY_CPY
	RETURN #2			@ return 2;

// 0xCC: CPY abs
opasm_cpy_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_CPY
	RETURN #4				@ return 4


/*******
 * DEC *
 *******/

.macro BODY_DEC
	sub %r1, #1				@ temp --;
	SETZN %r1				@ setzn(temp);
.endm

// 0xC6: DEC zp
opasm_dec_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_DEC
	WRITEBYTED %r1, %r0     @ writemem(addr,temp);
	RETURN #5				@ return 5;

// 0xD6: DEC zp,x
opasm_dec_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	BODY_DEC
	WRITEBYTED %r1, %r0     @ writemem(addr,temp);
	RETURN #5				@ return 5;

// 0xCE: DEC abs
opasm_dec_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_DEC
	WRITEBYTE  %r1, %r0     @ writemem(addr,temp);
	RETURN #6				@ return 6;

// 0xDE: DEC abs,x
opasm_dec_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_DEC
	WRITEBYTE  %r1, %r0     @ writemem(addr,temp);
	RETURN #7				@ return 7;


/*************
 * DEX / DEY *
 *************/

// 0xCA: DEX
opasm_dex:
	sub %r8, %r8, #1		@ cpu->x--;
	and %r8, %r8, #255
	SETZN %r8				@ setzn(cpu->x);
	RETURN #2				@ return 2;

// 0x88: DEY
opasm_dey:
	sub %r9, %r9, #1		@ cpu->y--;
	and %r9, %r9, #255
	SETZN %r9				@ setzn(cpu->y);
	RETURN #2				@ return 2;


/*******
 * EOR *
 *******/

.macro BODY_EOR
	eor %r7, %r7, %r1		@ cpu->a ^= temp;
	SETZN %r7				@ setzn(cpu->a);
.endm

// 0x49: EOR imm
opasm_eor_imm:
	READBYTE_PC %r1		@ cpu->a ^= readmem(cpu->pc); cpu->pc++;
	BODY_EOR
	RETURN #2			@ return 2;

// 0x45: EOR zp
opasm_eor_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_EOR
	RETURN #3				@ return 3

// 0x55: EOR zp,x
opasm_eor_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	BODY_EOR
	RETURN #3				@ return 3

// 0x4D: EOR abs
opasm_eor_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_EOR
	RETURN #4				@ return 4

// 0x59: EOR abs,y
opasm_eor_abs_y:
	START_ABS_Y				@ r0=address, r1=byte from address
	BODY_EOR
	RETURN #4				@ return 4

// 0x5D: EOR abs,x
opasm_eor_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_EOR
	RETURN #4				@ return 4

// 0x41: EOR (,x)
opasm_eor_indzx:
	START_INDZX
	READBYTE %r1, %r0		@ temp = readmem(addr);
	BODY_EOR
	RETURN #6				@ return 6

// 0x51: EOR (),y
opasm_eor_indzy:
	START_INDZY
	BODY_EOR
	RETURN #5				@ return 5


/*******
 * INC *
 *******/

.macro BODY_INC
	add %r1, #1				@ temp ++;
	SETZN %r1				@ setzn(temp);
.endm

// 0xE6: INC zp
opasm_inc_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_INC
	WRITEBYTED %r1, %r0     @ writemem(addr,temp);
	RETURN #5				@ return 5;

// 0xF6: INC zp,x
opasm_inc_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	BODY_INC
	WRITEBYTED %r1, %r0     @ writemem(addr,temp);
	RETURN #5				@ return 5;


// 0xEE: INC abs
opasm_inc_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_INC
	WRITEBYTE  %r1, %r0     @ writemem(addr,temp);
	RETURN #6				@ return 6;


// 0xFE: INC abs,x
opasm_inc_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_INC
	WRITEBYTE  %r1, %r0     @ writemem(addr,temp);
	RETURN #7				@ return 7;


/*************
 * INX / INY *
 *************/

// 0xE8: INX
opasm_inx:
	add %r8, %r8, #1		@ cpu->x++;
	and %r8, %r8, #255
	SETZN %r8				@ setzn(cpu->x);
	RETURN #2				@ return 2;


// 0xC8: INY
opasm_iny:
	add %r9, %r9, #1		@ cpu->y++;
	and %r9, %r9, #255
	SETZN %r9				@ setzn(cpu->y);
	RETURN #2				@ return 2;


/*******
 * JMP *
 *******/

// 0x4C: JMP
opasm_jmp:
	READWORD %r6, %r6
	RETURN #3

// 0x6C: JMP ()
opasm_jmp_ind:
	READWORD_PC %r0			@ register uint16_t addr=readwordpc();
	and %r1, %r0, #255
	cmp %r1, #255
	beq .+12
	READWORD %r6, %r0		@ cpu->pc=readword(addr);
	RETURN #5
	// JMP address lives in word spanning page boundary... have to do a silly thing.
	READBYTE %r1, %r0		@ get lo byte from top of page...
	and %r0, #0xff00			@ and hi byte from bottom of page!
	READBYTE %r2, %r0
	lsr %r6, %r2, #8
	orr %r6, %r1
	RETURN #5


/*******
 * JSR *
 *******/

opasm_jsr:
	READWORD %r3, %r6		@ uint16_t addr=readwordpc(); cpu->pc--;
	add %r6, %r6, #1
	PUSHWORD %r6, %r2
	mov %r6, %r3		@ cpu->pc=addr;
	RETURN #6;			@ return 6;


/*******
 * LAX *
 *******/

// 0xB7: LAX zp,y
opasm_lax_zp_y:
	READBYTE_PC %r0			@ uint16_t addr=readmem(cpu->pc); cpu->pc++;
	add %r0, %r0, %r9   	@ addr += cpu->9
	and %r0, %r0, #255  	@ addr &= 255 , i.e. keep in zero page
	READBYTE %r7, %r0   	@ cpu->a=cpu->x=readmem(addr);
	mov %r8, %r7
	SETZN %r7				@ setzn(cpu->a);
	RETURN #3				@ return 3;


/*******************
 * LDA / LDX / LDY *
 *******************/

// 0xA9: LDA imm
// 0xA2: LDX imm
// 0xA0: LDY imm
.macro LOAD_IMM dest
	READBYTE_PC \dest	@ cpu->a = readmem(cpu->pc); pc++;
	SETZN \dest			@ setzn(cpu->a);
	RETURN #2			@ return 2;
.endm
opasm_lda_imm:
	LOAD_IMM %r7
opasm_ldx_imm:
	LOAD_IMM %r8
opasm_ldy_imm:
	LOAD_IMM %r9

// 0xA5: LDA zp
// 0xA6: LDX zp
// 0xA4: LDY zp
.macro LOAD_ZP dest
	READBYTE_PC %r0		@ uint16_t addr=readmem(cpu->pc); cpu->pc++;
	READBYTE \dest, %r0	@ cpu->a=readmem(addr);
	SETZN \dest			@ setzn(cpu->a);
	RETURN #3			@ return 3;
.endm
opasm_lda_zp:
	LOAD_ZP %r7
opasm_ldx_zp:
	LOAD_ZP %r8
opasm_ldy_zp:
	LOAD_ZP %r9

// 0xB5: LDA zp,x
// 0xB6: LDX zp,y
// 0xB4: LDY zp,x
.macro LOAD_ZPO dest, offset
	READBYTE_PC %r0			@ uint16_t addr=readmem(cpu->pc); cpu->pc++;
	add %r0, %r0, \offset   @ addr += cpu->x
	and %r0, %r0, #255  	@ addr &= 255 , i.e. keep in zero page
	READBYTE \dest, %r0   	@ cpu->a=readmem(addr);
	SETZN \dest				@ setzn(cpu->a);
	RETURN #3				@ return 3;
.endm
opasm_lda_zp_x:
	LOAD_ZPO %r7, %r8
opasm_ldx_zp_y:
	LOAD_ZPO %r8, %r9
opasm_ldy_zp_x:
	LOAD_ZPO %r9, %r8

// 0xAC: LDY abs
// 0xAD: LDA abs
// 0xAE: LDX abs
.macro LOAD_ABS dest
	READWORD_PC %r0			@ uint16_t addr=readwordpc();
	READBYTE \dest, %r0   	@ cpu->a=readmem(addr);
	SETZN \dest				@ setzn(cpu->a);
	RETURN #4				@ return 4;
.endm
opasm_lda_abs:
	LOAD_ABS %r7
opasm_ldx_abs:
	LOAD_ABS %r8
opasm_ldy_abs:
	LOAD_ABS %r9

// 0xBD: LDA abs,x
// 0xB9: LDA abs,y
// 0xBE: LDX abs,y
// 0xBC: LDY abs,x
.macro LOAD_ABS_OFF dest, offs
	READWORD_PC %r0			@ uint16_t addr=readwordpc();
	add %r0, \offs
	READBYTE \dest, %r0   	@ cpu->a=readmem(addr);
	SETZN \dest				@ setzn(cpu->a);
	RETURN #4				@ return 4;
.endm
opasm_lda_abs_x:
	LOAD_ABS_OFF %r7, %r8
opasm_lda_abs_y:
	LOAD_ABS_OFF %r7, %r9
opasm_ldx_abs_y:
	LOAD_ABS_OFF %r8, %r9
opasm_ldy_abs_x:
	LOAD_ABS_OFF %r9, %r8

// 0xA1: LDA (,x)
opasm_lda_indzx:
	START_INDZX
	READBYTE %r7, %r0		@ cpu->a = readmem(addr);
	SETZN %r7
	RETURN #6

// 0xB1: LDA (),y
opasm_lda_y:
	READBYTE_PC %r0			@ register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	READWORD %r1, %r0 		@ register uint16_t addr=readword(temp);
	add %r1, %r1, %r9		@ addr += y
	READBYTE %r7, %r1		@ cpu->a=readmem(addr);
	SETZN %r7				@ setzn(cpu->a);
	RETURN #5


/*******
 * LSR *
 *******/

.macro BODY_LSR reg
	bic %r11, #1			@ clear FLAG_C
	lsrs \reg, #1			@ temp>>=1;
	orrcs %r11, #1			@ set FLAG_C if bit 0 carried over
	SETZN \reg				@ setzn(temp);
.endm

// 0x4A: LSR A
opasm_lsr_a:
	BODY_LSR %r7
	RETURN #2				@ return 2

// 0x46: LSR zp
opasm_lsr_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_LSR %r1
	WRITEBYTED %r1, %r0
	RETURN #5				@ return 5

// 0x56: LSR zp,x
opasm_lsr_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	BODY_LSR %r1
	WRITEBYTED %r1, %r0
	RETURN #5				@ return 5


// 0x4E: LSR abs
opasm_lsr_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_LSR %r1
	WRITEBYTE %r1, %r0		@ writemem(addr,temp);
	RETURN #6				@ return 6

// 0x5E: LSR abs,x
opasm_lsr_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_LSR %r1
	WRITEBYTE %r1, %r0		@ writemem(addr,temp);
	RETURN #7				@ return 7


/*******
 * NOP *
 *******/

// 0xEA: NOP
opasm_nop:
	RETURN #2

// 0x80: NOP imm		undocumented
// 0x89: NOP imm		undocumented
// 0xC2: NOP imm		undocumented
// 0xE2: NOP imm		undocumented
opasm_nop_imm:
	RETURN #2

// 0x04: NOP zp			undocumented
opasm_nop_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	RETURN #3				@ return 3;

// 0x14: NOP zp,x		undocumented
opasm_nop_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	RETURN #3				@ return 3;

// 0x0C: NOP abs		undocumented
opasm_nop_abs:
	START_ABS				@ r0=address, r1=byte from address
	RETURN #4				@ return 4;

// 0xFC: NOP abs,x			undocumented
opasm_nop_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	RETURN #4				@ return 4;


/*******
 * ORA *
 *******/

.macro BODY_ORA
	orr %r7, %r7, %r1		@ cpu->a|=readmem(addr);
	SETZN %r7				@ setzn(cpu->a);
.endm

// 0x09: ORA imm
opasm_ora_imm:
	READBYTE_PC %r1			@ cpu->a |= readmem(cpu->pc); cpu->pc++;
	BODY_ORA
	RETURN #2				@ return 2;

// 0x05: ORA zp
opasm_ora_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_ORA
	RETURN #3				@ return 3;

// 0x15: ORA zp,x
opasm_ora_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	BODY_ORA
	RETURN #3				@ return 3;

// 0x0D: ORA abs
opasm_ora_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_ORA
	RETURN #4				@ return 4;

// 0x19: ORA abs,y
opasm_ora_abs_y:
	START_ABS_Y				@ r0=address, r1=byte from address
	BODY_ORA
	RETURN #4				@ return 4;

// 0x1D: ORA abs,x
opasm_ora_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_ORA
	RETURN #4				@ return 4;

// 0x01: ORA (,x)
opasm_ora_indzx:
	START_INDZX
	READBYTE %r1, %r0		@ temp = readmem(addr);
	BODY_ORA
	RETURN #6				@ return 6;

// 0x11: ORA (),y
opasm_ora_indzy:
	START_INDZY
	BODY_ORA
	RETURN #5				@ return 5;


/*******
 * PHA *
 *******/

// 0x48: PHA
opasm_pha:
	PUSHBYTE %r7			@ push(cpu->a);
	RETURN #3				@ return 3;


/*******
 * PHP *
 *******/

// 0x08: PHP
opasm_php:
	orr %r0, %r11, #0x30 	@ register uint8_t  temp=0x30 | cpu->p;
	PUSHBYTE %r0	 		@ push(temp);
	RETURN #3


/*******
 * PLA *
 *******/

// 0x68: PLA
opasm_pla:
	PULLBYTE %r7			@ cpu->a = pull();
	SETZN %r7				@ setzn(cpu->a);
	RETURN #4				@ return 4;


/*******
 * PLP *
 *******/

// 0x28: PLP
opasm_plp:
	PULLBYTE %r1			@ uint8_t temp=pull();
	//TODO! cpu->takeint=(cpu->interrupt && !(cpu->p & FLAG_I));
	bic %r11, %r1, #0x30	@ cpu->p = temp & ~0x30;
	RETURN #-4;


/********************************
 * RLA : Undocumented ROL + AND *
 ********************************/
 .macro BODY_RLA
	lsl %r1, #1				@ temp<<=1;
	tst %r11, #1			@ if (FLAG_C set) temp |= 1;
	orrne %r1, #1
	bic %r11, #1			@ clear C
	tst %r1, #0x100			@ if (rotate carried) SET_FLAG(FLAG_C);
	orrne %r11, #1
	WRITEBYTED %r1, %r0
	SETZN %r1				@ setzn(temp);
.endm
opasm_rla_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_RLA
	RETURN #5				@ return 5
opasm_rla_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	BODY_RLA
	RETURN #5				@ return 5
opasm_rla_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_RLA
	RETURN #6				@ return 6
opasm_rla_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_RLA
	RETURN #6				@ return 6
opasm_rla_abs_y:
	START_ABS_Y				@ r0=address, r1=byte from address
	BODY_RLA
	RETURN #6				@ return 6
opasm_rla_indzx:
	START_INDZX
	READBYTE %r1, %r0		@ temp = readmem(addr);
	BODY_RLA
	RETURN #7
opasm_rla_indzy:
	START_INDZY
	BODY_RLA
	RETURN #7


/*******
 * ROL *
 *******/
 .macro BODY_ROL reg
	lsl \reg, #1			@ temp<<=1;
	tst %r11, #1			@ if (FLAG_C set) temp |= 1;
	orrne \reg, #1
	bic %r11, #1			@ clear C
	tst \reg, #0x100		@ if (rotate carried) SET_FLAG(FLAG_C);
	orrne %r11, #1
	SETZN \reg				@ setzn(temp);
.endm

// 0x2A: ROL A
opasm_rol_a:
	BODY_ROL %r7
	RETURN #2				@ return 2

// 0x26: ROL zp
opasm_rol_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_ROL %r1
	WRITEBYTED %r1, %r0		@ writemem(addr,temp);
	RETURN #3				@ return 3

// 0x36: ROL zp,x
opasm_rol_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	BODY_ROL %r1
	WRITEBYTED %r1, %r0		@ writemem(addr,temp);
	RETURN #5				@ return 5

// 0x2E: ROL abs
opasm_rol_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_ROL %r1
	WRITEBYTE %r1, %r0		@ writemem(addr,temp);
	RETURN #6				@ return 6

// 0x3E: ROL abs,x
opasm_rol_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_ROL %r1
	WRITEBYTE %r1, %r0		@ writemem(addr,temp);
	RETURN #6				@ return 6


/*******
 * ROR *
 *******/
.macro BODY_ROR reg
	tst %r11, #1			@ if FLAG_C set
	orrne \reg, #0x100		@     temp |= 0x100;
	bic %r11, #1			@ clear FLAG_C
	lsrs \reg, #1			@ temp>>=1;
	orrcs %r11, #1			@ set FLAG_C if bit 0 carried over
	SETZN \reg				@ setzn(temp);
.endm

// 0x6A: ROR A
opasm_ror_a:
	BODY_ROR %r7
	RETURN #2				@ return 2

// 0x66: ROR zp
opasm_ror_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_ROR %r1
	WRITEBYTED %r1, %r0
	RETURN #5				@ return 5

// 0x76: ROR zp,x
opasm_ror_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	BODY_ROR %r1
	WRITEBYTED %r1, %r0
	RETURN #5				@ return 5

// 0x6E: ROR abs
opasm_ror_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_ROR %r1
	WRITEBYTE %r1, %r0		@ writemem(addr,temp);
	RETURN #6				@ return 6

// 0x7E: ROR abs,x
opasm_ror_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_ROR %r1
	WRITEBYTE %r1, %r0		@ writemem(addr,temp);
	RETURN #7				@ return 7


/*******
 * RRA *
 *******/

.macro BODY_RRA
	tst %r11, #1			@ if FLAG_C set
	orrne %r1, #0x100		@     temp |= 0x100;
	bic %r11, #1			@ clear FLAG_C
	lsrs %r1, #1			@ temp>>=1;
	//orrcs %r11, #1			@ set FLAG_C if bit 0 carried over
	WRITEBYTE %r1, %r0		@ writemem(addr,temp);
	BODY_ADC
.endm


// 0x67: RRA zp			undocumented
opasm_rra_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_RRA
	RETURN #5				@ return 5;

// 0x77: RRA zp,x		undocumented
opasm_rra_zp_x:
	START_ZP_X				@ r0=zp address, r1=byte from zp address
	BODY_RRA
	RETURN #5				@ return 5;

// 0x6F: RRA abs		undocumented
opasm_rra_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_RRA
	RETURN #6				@ return 6

// 0x7B: RRA abs,y		undocumented
opasm_rra_abs_y:
	START_ABS_Y				@ r0=address, r1=byte from address
	BODY_RRA
	RETURN #6				@ return 6

// 0x7F: RRA abs,x		undocumented
opasm_rra_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_RRA
	RETURN #6				@ return 6

// 0x63: RRA (,x)		undocumented
opasm_rra_indzx:
	START_INDZX
	READBYTE %r1, %r0		@ temp = readmem(addr);
	BODY_RRA
	RETURN #7

// 0x73: RRA (),y		undocumented
opasm_rra_indzy:
	START_INDZY
	BODY_RRA
	RETURN #7

/*******
 * RTI *
 *******/

// 0x40: RTI
opasm_rti:
	PULLBYTE %r11			@ uint8_t temp=pull();
	and %r11, #0xcf 		@ cpu->p=temp & ~0x30;
	PULLWORD %r6			@ cpu->pc=pull(); cpu->pc|=(pull()<<8);
	RETURN #6;


/*******
 * RTS *
 *******/

// 0x60: RTS
opasm_rts:
	PULLWORD %r6			@ cpu->pc=pull(); cpu->pc|=(pull()<<8);
	add %r6, #1				@ cpu->pc++;
	RETURN #6;



/*******
 * SBC *
 *******/

.macro BODY_SBC
	tst %r11, #8		@ if (cpu->p & FLAG_D) do BCD arithmetic in a C function
	bne .+80
	tst %r11, #1			@ r1 = temp + (cpu->p & FLAG_C)?0:1;
	addeq %r1, #1
	sub %r2, %r7, %r1   	@ r2 = cpu->a - r1;
	bic %r11, #0x41     	@ clear C and V flags
	tst %r2, #256
	orreq %r11, #1			@ set FLAG_C (ONLY IF RESULT DID *NOT* BORROW... THIS IS INVERSE TO ADC)
	eor %r0, %r7, %r1		@ r0 = cpu->a ^ temp
	lsls %r0, #24
	bmi .+16
	eor %r0, %r7, %r2		@ r1 = cpu->a ^ result_of_addition
	lsls %r0, #24
	orrmi %r11, #0x40		@ set FLAG_V
	and %r7, %r2, #255		@ cpu->a = result_of_addition & 255
	SETZN %r7				@ setzn(cpu->a);   <-- 5 instructions
	b .+36
	push {lr}
	strb %r7,  [%r4, #6]	// update A
	strb %r11, [%r4, #10]   // update P
	mov %r0, %r4			// r0=cpu*, r1=imm
	bl sbc_bcd
	pop {lr}
	ldrb %r7,  [%r4, #6]	// update A
	ldrb %r11, [%r4, #10]   // update P
.endm

// 0xE9: SBC imm
// 0xEB: SBC imm
opasm_sbc_imm:
	READBYTE_PC %r1		@ register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	BODY_SBC
	RETURN #2

// 0xE5: SBC zp
opasm_sbc_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_SBC
	RETURN #3				@ return 3;

// 0xF5: SBC zp,x
opasm_sbc_zp_x:
	START_ZP_X				@ r0=zp address, r1=byte from zp address
	BODY_SBC
	RETURN #3				@ return 3;

// 0xED: SBC abs
opasm_sbc_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_SBC
	RETURN #4				@ return 4

// 0xF9: SBC abs,y
opasm_sbc_abs_y:
	START_ABS_Y				@ r0=address, r1=byte from address
	BODY_SBC
	RETURN #4				@ return 4

// 0xFD: SBC abs,x
opasm_sbc_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_SBC
	RETURN #4				@ return 4

// 0xE1: SBC (,x)
opasm_sbc_indzx:
	START_INDZX
	READBYTE %r1, %r0		@ temp = readmem(addr);
	BODY_SBC
	RETURN #6				@ return 6

// 0xF1: SBC (),y
opasm_sbc_indzy:
	START_INDZY
	BODY_SBC
	RETURN #5				@ return 5


/*******************
 * SEC / SED / SEI *
 *******************/

opasm_sec:
	orr %r11, #1	@ cpu->p |= FLAG_C;
	RETURN #2
opasm_sed:
	orr %r11, #8	@ cpu->p |= FLAG_D;
	RETURN #2
opasm_sei:
	UPDATE_TAKEINT			@ cpu->takeint=(cpu->interrupt && !(cpu->p & FLAG_I));
	orr %r11, #4			@ cpu->p |= FLAG_I;
	RETURN #-2;				@ return -2;


/**************************************
 * SLO - Undocumented Shift Left & Or *
 **************************************/

.macro BODY_SLO
	lsl %r1, #1				@ temp<<=1;
	and %r1, #255
	WRITEBYTE %r1, %r0		@ writemem(addr,temp);
	orr %r7, %r7, %r1		@ cpu->a|=temp;
	SETZN %r7				@ setzn(cpu->a);
.endm
opasm_slo_zp:
	START_ZP				@ r0=zp address, r1=byte from zp address
	BODY_SLO
	RETURN #5				@ return 5;
opasm_slo_zp_x:
	START_ZP_X				@ r1=byte from zp, r0=zp address
	BODY_SLO
	RETURN #5				@ return 5;
opasm_slo_abs:
	START_ABS				@ r0=address, r1=byte from address
	BODY_SLO
	RETURN #6				@ return 4;
opasm_slo_abs_y:
	START_ABS_Y				@ r0=address, r1=byte from address
	BODY_SLO
	RETURN #6				@ return 6;
opasm_slo_abs_x:
	START_ABS_X				@ r0=address, r1=byte from address
	BODY_SLO
	RETURN #6				@ return 6;
opasm_slo_indzx:
	START_INDZX
	READBYTE %r1, %r0		@ temp = readmem(addr);
	BODY_SLO
	RETURN #7				@ return 7;
opasm_slo_indzy:
	START_INDZY
	BODY_SLO
	RETURN #7				@ return 7;


/*******************
 * STA / STX / STY *
 *******************/

// 0x8C: STY abs
opasm_sty_abs:
	READWORD_PC %r0			@ uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	WRITEBYTE %r9, %r0		@ writemem(addr, cpu->y);
	RETURN #4				@ return 4

// 0x85: STA zp
// 0x84: STY zp
// 0x86: STX zp
.macro STORE_ZP val
	READBYTE_PC %r0			@ uint16_t addr=readmem(cpu->pc); cpu->pc++;
	WRITEBYTE \val, %r0		@ writemem(addr,cpu->a);
	RETURN #3				@ return 3;
.endm
opasm_sta_zp:
	STORE_ZP %r7
opasm_stx_zp:
	STORE_ZP %r8
opasm_sty_zp:
	STORE_ZP %r9


// 0x95: STA zp,x
// 0x96: STX zp,y
// 0x94: STY zp,x
.macro STORE_ZPO dest, offset
	READBYTE_PC %r0			@ uint16_t addr=readmem(cpu->pc); cpu->pc++;
	add %r0, %r0, \offset   @ addr += cpu->x
	and %r0, %r0, #255  	@ addr &= 255 , i.e. keep in zero page
	WRITEBYTE \dest, %r0   	@ writemem(addr, a);
	RETURN #4				@ return 4;
.endm
opasm_sta_zp_x:
	STORE_ZPO %r7, %r8
opasm_stx_zp_y:
	STORE_ZPO %r8, %r9
opasm_sty_zp_x:
	STORE_ZPO %r9, %r8

// 0x8D: STA abs
opasm_sta_abs:
	READWORD_PC %r0			@ uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	WRITEBYTE %r7, %r0		@ writemem(addr, cpu->a);
	RETURN #4				@ return 4

// 0x8E: STX abs
opasm_stx_abs:
	READWORD_PC %r0			@ uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	WRITEBYTE %r8, %r0		@ writemem(addr, cpu->x);
	RETURN #4				@ return 4

// 0x99: STA abs,y
opasm_sta_abs_y:
	READWORD_PC %r0			@ uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	add %r0, %r9			@ addr += cpu->y;
	WRITEBYTE %r7, %r0		@ writemem(addr, cpu->a);
	RETURN #5				@ return 5

// 0x9D: STA abs,x
opasm_sta_abs_x:
	READWORD_PC %r0			@ uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	add %r0, %r8			@ addr += cpu->x;
	WRITEBYTE %r7, %r0		@ writemem(addr, cpu->a);
	RETURN #5				@ return 5

// 0x81: STA (,x)
opasm_sta_indzx:
	START_INDZX
	WRITEBYTE %r7, %r0
	RETURN #6

// 0x91: STA (),y
opasm_sta_indzy:
	READBYTE_PC %r0			@ register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	READWORD %r1, %r0 		@ register uint16_t addr=readword(temp);
	add %r1, %r1, %r9		@ addr += y
	WRITEBYTE %r7, %r1		@ writemem(addr, cpu->a);
	RETURN #5


/*************************************
 * TAX / TAY / TSX / TXA / TXS / TYA *
 *************************************/

// 0xAA: TAX
opasm_tax:
	mov %r8, %r7			@ cpu->x = cpu->a;
	SETZN %r8				@ setzn(cpu->x);
	RETURN #2				@ return 2;

// 0xA8: TAY
opasm_tay:
	mov %r9, %r7			@ cpu->y=cpu->a;
	SETZN %r9				@ setzn(cpu->y);
	RETURN #2				@ return 2;

// 0xBA: TSX
opasm_tsx:
	mov %r8, %r10			@ cpu->x=cpu->s;
	SETZN %r8				@ setzn(cpu->x);
	RETURN #2				@ return 2;

// 0x8A: TXA
opasm_txa:
	mov %r7, %r8			@ cpu->a=cpu->x;
	SETZN %r7				@ setzn(cpu->a);
	RETURN #2				@ return 2;

// 0x9A: TXS
opasm_txs:
	mov %r10, %r8			@ cpu->s=cpu->x;
	RETURN #2				@ return 2;

// 0x98: TYA
opasm_tya:
	mov %r7, %r9			@ cpu->a=cpu->y;
	SETZN %r7				@ setzn(cpu->a);
	RETURN #2				@ return 2;





/********************
 * Undefined Opcode *
 ********************/
opasm_undef:
	mov %r0, %r4
	bl log_undef_opcode
	sub %r6, %r6, #1;		@ cpu->pc--;      	// PC never moves on
	mov %r0, #0
    str %r0, [%r4, #20]		@ cpu->takeint=0; 	// Interrupts never occur
    str %r0, [%r4, #16]		@ cpu->nmi=0;  		// NMIs never occur
    RETURN #65536			@ return 100000; 	// Actually lasts forever, but the above code keeps executing HLT forever


fns_asm:
	.word 0 				// 0x00 BRK
	.word opasm_ora_indzx 	// 0x01 ORA (,x)
	.word opasm_undef
	.word opasm_slo_indzx   // 0x03: SLO (,x)		undocumented
	.word opasm_nop_zp  	// 0x04: NOP zp			undocumented
	.word opasm_ora_zp		// 0x05: ORA zp
	.word opasm_asl_zp		// 0x06: ASL zp
	.word opasm_slo_zp  	// 0x07: SLO zp			undocumented
	.word opasm_php     	// 0x08: PHP
	.word opasm_ora_imm 	// 0x09: ORA imm
	.word opasm_asl_a   	// 0x0A: ASL A
	.word 0 				// 0x0B: ANC imm		undocumented
	.word opasm_nop_abs 	// 0x0C: NOP abs		undocumented
	.word opasm_ora_abs 	// 0x0D: ORA abs
	.word opasm_asl_abs		// 0x0E: ASL abs
	.word opasm_slo_abs 	// 0x0F: SLO abs		undocumented
	.word opasm_bpl 		// 0x10: BPL
	.word opasm_ora_indzy  	// 0x11: ORA (),y
	.word opasm_undef
	.word opasm_slo_indzy   // 0x13: SLO (),y		undocumented
	.word opasm_nop_zp_x	// 0x14: NOP zp,x		undocumented
	.word opasm_ora_zp_x	// 0x15: ORA zp,x
	.word opasm_asl_zp_x	// 0x16: ASL zp,x
	.word opasm_slo_zp_x	// 0x17: SLO zp,x		undocumented
	.word opasm_clc 		// 0x18: CLC
	.word opasm_ora_abs_y	// 0x19: ORA abs,y
	.word opasm_nop     	// 0x1A: NOP			undocumented
	.word opasm_slo_abs_y	// 0x1B: SLO abs,y		undocumented
	.word opasm_nop_abs_x	// 0x1C: NOP abs,x		undocumented
	.word opasm_ora_abs_x	// 0x1D: ORA abs,x
	.word opasm_asl_abs_x	// 0x1E: ASL abs,x
	.word opasm_slo_abs_x	// 0x1F: SLO abs,x		undocumented
	.word opasm_jsr     	// 0x20: JSR
	.word opasm_and_indzx   // 0x21: AND (,x)
	.word opasm_undef
	.word opasm_rla_indzx	// 0x23: RLA (,x)		undocumented
	.word opasm_bit_zp		// 0x24: BIT zp
	.word opasm_and_zp		// 0x25: AND zp
	.word opasm_rol_zp  	// 0x26: ROL zp
	.word opasm_rla_zp  	// 0x27: RLA zp			undocumented
	.word opasm_plp     	// 0x28: PLP
	.word opasm_and_imm		// 0x29: AND
	.word opasm_rol_a   	// 0x2A: ROL A
	.word 0 				// 0x2B: ANC imm		undocumented
	.word opasm_bit_abs		// 0x2C: BIT abs
	.word opasm_and_abs		// 0x2D: AND abs
	.word opasm_rol_abs		// 0x2E: ROL abs
	.word opasm_rla_abs 	// 0x2F: RLA abs		undocumented
	.word opasm_bmi 		// 0x30: BMI
	.word opasm_and_indzy   // 0x31: AND (),y
	.word opasm_undef
	.word opasm_rla_indzy   // 0x33: RLA (),y		undocumented
	.word opasm_nop_zp_x	// 0x34: NOP zp,x		undocumented
	.word opasm_and_zp_x	// 0x35: AND zp,x
	.word opasm_rol_zp_x	// 0x36: ROL zp,x
	.word opasm_rla_zp_x	// 0x37: RLA zp,x		undocumented
	.word opasm_sec 		// 0x38: SEC
	.word opasm_and_abs_y	// 0x39: AND abs,y
	.word opasm_nop	    	// 0x3A: NOP			undocumented
	.word opasm_rla_abs_y	// 0x3B: RLA abs,y		undocumented
	.word opasm_nop_abs_x	// 0x3C: NOP abs,x		undocumented
	.word opasm_and_abs_x	// 0x3D: AND abs,x
	.word opasm_rol_abs_x	// 0x3E: ROL abs,x
	.word opasm_rla_abs_x	// 0x3F: RLA abs,x		undocumented
	.word opasm_rti			// 0x40: RTI
	.word opasm_eor_indzx	// 0x41: EOR (,x)
	.word opasm_undef
	.word 0   				// 0x43: SRE (,x)		undocumented
	.word opasm_nop_zp  	// 0x44: NOP zp
	.word opasm_eor_zp  	// 0x45: EOR zp
	.word opasm_lsr_zp  	// 0x46: LSR zp
	.word 0  				// 0x47: SRE zp			undocumented
	.word opasm_pha     	// 0x48: PHA
	.word opasm_eor_imm 	// 0x49: EOR imm
	.word opasm_lsr_a   	// 0x4A: LSR A
	.word 0 				// 0x4B: ASR imm		undocumented
	.word opasm_jmp			// 0x4C: JMP
	.word opasm_eor_abs		// 0x4D: EOR abs
	.word opasm_lsr_abs		// 0x4E: LSR abs
	.word 0 				// 0x4F: SRE abs		undocumented
	.word opasm_bvc 		// 0x50: BVC
	.word opasm_eor_indzy	// 0x51: EOR (),y
	.word opasm_undef
	.word 0   				// 0x53: SRE (),y		undocumented
	.word opasm_nop_zp_x	// 0x54: NOP zp,x
	.word opasm_eor_zp_x	// 0x55: EOR zp,x
	.word opasm_lsr_zp_x	// 0x56: LSR zp,x
	.word 0					// 0x57: SRE zp,x		undocumented
	.word opasm_cli     	// 0x58: CLI
	.word opasm_eor_abs_y	// 0x59: EOR abs,y
	.word opasm_nop 		// 0x5A: NOP			undocumented
	.word 0					// 0x5B: SRE abs,y		undocumented
	.word opasm_nop_abs_x	// 0x5C: NOP abs,x		undocumented
	.word opasm_eor_abs_x	// 0x5D: EOR abs,x
	.word opasm_lsr_abs_x	// 0x5E: LSR abs,x
	.word 0					// 0x5F: SRE abs,x		undocumented
	.word opasm_rts 		// 0x60: RTS
	.word opasm_adc_indzx   // 0x61: ADC (,x)
	.word opasm_undef
	.word opasm_rra_indzx   // 0x63: RRA (,x)		undocumented
	.word opasm_nop_zp  	// 0x64: NOP zp			undocumented
	.word opasm_adc_zp  	// 0x65: ADC zp
	.word opasm_ror_zp		// 0x66: ROR zp
	.word opasm_rra_zp  	// 0x67: RRA zp			undocumented
	.word opasm_pla    		// 0x68: PLA
	.word opasm_adc_imm 	// 0x69: ADC imm
	.word opasm_ror_a   	// 0x6A: ROR A
	.word 0     			// 0x6B: ARR			undocumented
	.word opasm_jmp_ind		// 0x6C: JMP ()
	.word opasm_adc_abs 	// 0x6D: ADC abs
	.word opasm_ror_abs 	// 0x6E: ROR abs
	.word opasm_rra_abs 	// 0x6F: RRA abs		undocumented
	.word opasm_bvs     	// 0x70: BVS
	.word opasm_adc_indzy   // 0x71: ADC (),y
	.word opasm_undef
	.word opasm_rra_indzy  	// 0x73: RRA (),y		undocumented
	.word opasm_nop_zp_x	// 0x74: NOP zp,x		undocumented
	.word opasm_adc_zp_x 	// 0x75: ADC zp,x
	.word opasm_ror_zp_x	// 0x76: ROR zp,x
	.word opasm_rra_zp_x	// 0x77: RRA zp,x		undocumented
	.word opasm_sei     	// 0x78: SEI
	.word opasm_adc_abs_y	// 0x79: ADC abs,y
	.word opasm_nop			// 0x7A: NOP			undocumented
	.word opasm_rra_abs_y	// 0x7B: RRA abs,y		undocumented
	.word opasm_undef
	.word opasm_adc_abs_x	// 0x7D: ADC abs,x
	.word opasm_ror_abs_x	// 0x7E: ROR abs,x
	.word opasm_rra_abs_x	// 0x7F: RRA abs,x		undocumented
	.word opasm_nop_imm  	// 0x80: NOP imm		undocumented
	.word opasm_sta_indzx	// 0x81: STA (,x)
	.word opasm_nop_imm 	// 0x82: NOP imm		undocumented
	.word 0   				// 0x83: SAX (,x)		undocumented
	.word opasm_sty_zp  	// 0x84: STY zp
	.word opasm_sta_zp  	// 0x85: STA zp
	.word opasm_stx_zp  	// 0x86: STX zp
	.word 0  				// 0x87: SAX zp			undocumented
	.word opasm_dey     	// 0x88: DEY
	.word opasm_nop_imm 	// 0x89: NOP imm		undocumented
	.word opasm_txa     	// 0x8A: TXA
	.word 0     			// 0x8B: ANE			undocumented
	.word opasm_sty_abs 	// 0x8C: STY abs
	.word opasm_sta_abs 	// 0x8D: STA abs
	.word opasm_stx_abs 	// 0x8E: STX abs
	.word 0 				// 0x8F: SAX abs		undocumented
	.word opasm_bcc     	// 0x90: BCC
	.word opasm_sta_indzy	// 0x91: STA (),y
	.word opasm_undef
	.word 0   				// 0x93: SHA (),y		undocumented
	.word opasm_sty_zp_x	// 0x94: STY zp,x
	.word opasm_sta_zp_x	// 0x95: STA zp,x
	.word opasm_stx_zp_y	// 0x96: STX zp,y
	.word 0					// 0x97: SAX zp,y		undocumented
	.word opasm_tya     	// 0x98: TYA
	.word opasm_sta_abs_y	// 0x99: STA abs,y
	.word opasm_txs     	// 0x9A: TXS
	.word 0					// 0x9B: SHS abs,y		undocumented
	.word 0					// 0x9C: SHY abs,x		undocumented
	.word opasm_sta_abs_x	// 0x9D: STA abs,x
	.word 0					// 0x9E: SHX abs,y		undocumented
	.word 0					// 0x9F: SHA abs,y		undocumented
	.word opasm_ldy_imm 	// 0xA0: LDY imm
	.word opasm_lda_indzx   // 0xA1: LDA (,x)
	.word opasm_ldx_imm 	// 0xA2: LDX imm
	.word 0   				// 0xA3: LAX (,y)		undocumented
	.word opasm_ldy_zp  	// 0xA4: LDY zp
	.word opasm_lda_zp  	// 0xA5: LDA zp
	.word opasm_ldx_zp		// 0xA6: LDX zp
	.word 0  				// 0xA7: LAX zp			undocumented
	.word opasm_tay     	// 0xA8: TAY
	.word opasm_lda_imm 	// 0xA9: LDA imm
	.word opasm_tax     	// 0xAA: TAX
	.word 0     			// 0xAB: LAX			undocumented
	.word opasm_ldy_abs 	// 0xAC: LDY abs
	.word opasm_lda_abs 	// 0xAD: LDA abs
	.word opasm_ldx_abs 	// 0xAE: LDX abs
	.word 0 				// 0xAF: LAX abs
	.word opasm_bcs     	// 0xB0: BCS
	.word opasm_lda_y   	// 0xB1: LDA (),y
	.word opasm_undef
	.word 0  				// 0xB3: LAX (),y
	.word opasm_ldy_zp_x	// 0xB4: LDY zp,x
	.word opasm_lda_zp_x	// 0xB5: LDA zp,x
	.word opasm_ldx_zp_y	// 0xB6: LDX zp,y
	.word opasm_lax_zp_y	// 0xB7: LAX zp,y
	.word opasm_clv     	// 0xB8: CLV
	.word opasm_lda_abs_y	// 0xB9: LDA abs,y
	.word opasm_tsx     	// 0xBA: TSX
	.word 0					// 0xBB: LAS abs,y		undocumented
	.word opasm_ldy_abs_x	// 0xBC: LDY abs,x
	.word opasm_lda_abs_x	// 0xBD: LDA abs,x
	.word opasm_ldx_abs_y	// 0xBE: LDX abs,y
	.word 0					// 0xBF: LAX abs,y
	.word opasm_cpy_imm 	// 0xC0: CPY imm
	.word opasm_cmp_indzx	// 0xC1: CMP (,x)
	.word opasm_nop_imm 	// 0xC2: NOP imm		undocumented
	.word 0					// 0xC3: DCP (,x)		undocumented
	.word opasm_cpy_zp  	// 0xC4: CPY zp
	.word opasm_cmp_zp		// 0xC5: CMP zp
	.word opasm_dec_zp  	// 0xC6: DEC zp
	.word 0  				// 0xC7: DCP zp			undocumented
	.word opasm_iny     	// 0xC8: INY
	.word opasm_cmp_imm 	// 0xC9: CMP imm
	.word opasm_dex     	// 0xCA: DEX
	.word 0 				// 0xCB: SBX imm		undocumented
	.word opasm_cpy_abs		// 0xCC: CPY abs
	.word opasm_cmp_abs		// 0xCD: CMP abs
	.word opasm_dec_abs		// 0xCE: DEC abs
	.word 0 				// 0xCF: DCP abs		undocumented
	.word opasm_bne     	// 0xD0: BNE
	.word opasm_cmp_indzy	// 0xD1: CMP (),y
	.word opasm_undef
	.word 0					// 0xD3: DCP (),y		undocumented
	.word opasm_nop_zp_x	// 0xD4: NOP zp,x		undocumented
	.word opasm_cmp_zp_x	// 0xD5: CMP zp,x
	.word opasm_dec_zp_x	// 0xD6: DEC zp,x
	.word 0					// 0xD7: DCP zp,x		undocumented
	.word opasm_cld     	// 0xD8: CLD
	.word opasm_cmp_abs_y	// 0xD9: CMP abs,y
	.word opasm_nop     	// 0xDA: NOP			undocumented
	.word 0					// 0xDB: DCP abs,y		undocumented
	.word opasm_nop_abs_x	// 0xDC: NOP abs,x		undocumented
	.word opasm_cmp_abs_x	// 0xDD: CMP abs,x
	.word opasm_dec_abs_x	// 0xDE: DEC abs,x
	.word 0					// 0xDF: DCP abs,x		undocumented
	.word opasm_cpx_imm 	// 0xE0: CPX imm
	.word opasm_sbc_indzx	// 0xE1: SBC (,x)
	.word opasm_nop_imm 	// 0xE2: NOP imm		undocumented
	.word 0					// 0xE3: ISB (,x)		undocumented
	.word opasm_cpx_zp  	// 0xE4: CPX zp
	.word opasm_sbc_zp  	// 0xE5: SBC zp
	.word opasm_inc_zp  	// 0xE6: INC zp
	.word 0  				// 0xE7: ISB zp			undocumented
	.word opasm_inx     	// 0xE8: INX
	.word opasm_sbc_imm 	// 0xE9: SBC imm
	.word opasm_nop     	// 0xEA: NOP
	.word opasm_sbc_imm 	// 0xEB: SBC imm		undocumented
	.word opasm_cpx_abs		// 0xEC: CPX abs
	.word opasm_sbc_abs 	// 0xED: SBC abs
	.word opasm_inc_abs 	// 0xEE: INC abs
	.word 0 				// 0xEF: ISB abs		undocumented
	.word opasm_beq    		// 0xF0: BEQ
	.word opasm_sbc_indzy	// 0xF1: SBC (),y
	.word opasm_undef
	.word 0					// 0xF3: ISB (),y		undocumented
	.word opasm_nop_zp_x 	// 0xF4: NOP zp,x		undocumented
	.word opasm_sbc_zp_x	// 0xF5: SBC zp,x
	.word opasm_inc_zp_x	// 0xF6: INC zp,x
	.word 0					// 0xF7: ISB zp,x		undocumented
	.word opasm_sed     	// 0xF8: SED
	.word opasm_sbc_abs_y	// 0xF9: SBC abs,y
	.word opasm_nop     	// 0xFA: NOP			undocumented
	.word 0					// 0xFB: ISB abs,y		undocumented
	.word opasm_nop_abs_x	// 0xFC: NOP abs,x		undocumented
	.word opasm_sbc_abs_x	// 0xFD: SBC abs,x
	.word opasm_inc_abs_x	// 0xFE: INC abs,x
	.word 0					// 0xFF: ISB abs,x		undocumented

