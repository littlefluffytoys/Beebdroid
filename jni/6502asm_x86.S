# 6502asm.S
#
# X86 assembly version of (some of) 6502.c.
#
# Written by Reuben Scratton for Little Fluffy Toys Ltd in early Sept 2015.
#

.intel_syntax noprefix

.text
.global exec6502
.global acpu

# This implementation keeps 6502 state entirely in ARM registers during exec6502(), in order to reduce memory accesses.
# We haven't yet exploited the performance gains because we are still calling do_poll() after every
# instruction (i.e. pushing 6502 state on the stack with every emulated instruction). A goal for future development
# is to call do_poll() far less frequently without interfering with the accuracy of the emulation.
#
#  r4 = cpu*    EBP
#  r5 = mem     ESI
#  r6 = PC      di
#  r7 = A       cl
#  r8 = X       dl
#  r9 = Y       dh
# r10 = S       ch
# r11 = P

.macro C_CALL_PRE
	push %ecx
	push %edx
	//pusha
.endm

.macro C_CALL_POST
	pop %edx
	pop %ecx
	//popa
.endm

.macro LOG_CPU
    movw [%ebp+4], %di 	#  di = PC
    movb [%ebp+6], %cl 	#  cl = A
    movb [%ebp+7], %dl 	#  dl = X
    movb [%ebp+8], %dh 	#  r9 = Y
    movb [%ebp+9], %ch 	#  r10 = S
    pusha
	push %ebp
	call log_cpu
	add %esp,4
	popa
    movw %di,  [%ebp+4] 	#  di = r6  = PC
    movb %cl,  [%ebp+6] 	#  cl = r7  = A
    movb %dl,  [%ebp+7] 	#  dl = r8  = X
    movb %dh,  [%ebp+8] 	#  dh = r9  = Y
    movb %ch,  [%ebp+9] 	#  ch = r10 = S
.endm

.macro LOG arg
    pusha
    mov %eax, \arg
	push %eax
	call log_asm
	add %esp,4
	popa
.endm
.macro LOG_WRITEBYTE val8, addr16
    pusha
    mov %edx,0
    mov %dx, \addr16
	push %edx
	mov %edx,0
	mov %dl, \val8
	push %edx
	call log_write
	add %esp,8
	popa
.endm


// branch without labels: count number of lines to skip over, add 1, multiply by 4. So to skip 2 lines, "b .+12"

.macro PUSH_BYTE reg8
    movzx %ebx, %ch
	mov byte ptr [%esi+%ebx+0x100], \reg8    # mem+0x100 = bottom of stack space
	dec %ch
.endm
.macro PULLBYTE
	inc %ch
    movzx %eax, %ch
	movb %al, [%esi + %eax + 0x100]  	# scrapreg = mem+0x100, i.e. bottom of stack space
.endm

.macro PUSHWORD reg16
	dec %ch
    movzx %eax, %ch
	mov word ptr [%esi+%eax+0x100], \reg16    # mem+0x100 = bottom of stack space
	dec %ch
.endm
.macro PULLWORD reg16
	inc %ch
    movzx %eax, %ch
	movw \reg16, word ptr [%esi+%eax+0x100]
	inc %ch
.endm

.macro UPDATE_TAKEINT 	# cpu->takeint=(cpu->interrupt && !(cpu->p & FLAG_I));
	cmp dword ptr [%ebp+12], 0	# r12 = cpu->interrupt
	jz  1f
	test byte ptr [%ebp+10], 4  		# test the FLAG_I
	jnz 1f
	mov dword ptr [%ebp+20], 1
	jmp 2f
1:
	mov dword ptr [%ebp+20], 0   				#cpu->take_int = r1
2:
.endm

exec6502:
	pusha

	# Keep CPU* in EBP
	lea %ebp,acpu

    # update cpu->cycles. Note the constant value 40000 cannot be encoded and has to be ldr'd indirectly.
    mov  %eax,[%ebp+24]
    add  %eax, 40000
    mov  [%ebp+24], %eax

    # load some of 6502 CPU state into registers
    mov  %esi, [%ebp] 		# ESI = r5  = mem
    mov  %edi, 0
    movw %di,  [%ebp+4] 	#  di = r6  = PC
    movb %cl,  [%ebp+6] 	#  cl = r7  = A
    movb %dl,  [%ebp+7] 	#  dl = r8  = X
    movb %dh,  [%ebp+8] 	#  dh = r9  = Y
    movb %ch,  [%ebp+9] 	#  ch = r10 = S

    // eax,ebx are free for general use
loop:
    //LOG_CPU

	# Get the opcode into eax and update cpu->PC
	xor %eax,%eax
	movb %al,[%esi+%edi]
	inc %di

	# Get the ASM function. If this doesnt exist, fall back to C version.
	lea  %ebx, fns_asm
	mov  %ebx, [%ebx + %eax*4]
	cmp  %ebx, 0
	jz  use_c_fn

	# Call assembly function
	call %ebx
	jmp done_call

	# Get the C function pointer and call it, updating and restoring the in-memory 6502 state before and after the call.
use_c_fn:
	lea  %ebx, fns
	mov  %ebx, [%ebx + %eax*4]
    movw [%ebp+4], %di 	#  di = PC
    movb [%ebp+6], %cl 	#  cl = A
    movb [%ebp+7], %dl 	#  dl = X
    movb [%ebp+8], %dh 	#  r9 = Y
    movb [%ebp+9], %ch 	#  r10 = S
	push %ebp  	# Sole parameter for C opcode functions is r0 = CPU*
	call %ebx
	add %esp, 4
    mov  %edi, 0
    movw %di,  [%ebp+4] 	#  di = r6  = PC
    movb %cl,  [%ebp+6] 	#  cl = r7  = A
    movb %dl,  [%ebp+7] 	#  dl = r8  = X
    movb %dh,  [%ebp+8] 	#  dh = r9  = Y
    movb %ch,  [%ebp+9] 	#  ch = r10 = S

done_call:
	bt  %eax, 31
	jc  already_updated_interrupts
# cpu->takeint=(cpu->interrupt && !(cpu->p & FLAG_I));
	UPDATE_TAKEINT
	jmp done_interrupts
already_updated_interrupts:
	xor  %eax, 0xFFFFFFFF
	inc  %eax
done_interrupts:
    push %eax

	# Update interrupts
	cmp dword ptr [%ebp+16], 0   # ebp+16 = cpu->nmi
	jz no_nmi
	mov dword ptr [%ebp+16], 0
	pushw 0xfffa
	jmp do_interrupt
no_nmi:
	cmp dword ptr [%ebp+20], 0   # ebp+20 = cpu->take_int
	jz no_interrupt
	mov dword ptr [%ebp+20], 0
	pushw 0xfffe
do_interrupt:
	PUSHWORD %di			# push(cpu->pc)
	movzx %eax, byte ptr [%ebp+10]
	or  %eax, 0x20   	# uint8_t temp = cpu->p | 0x20;
	PUSH_BYTE %al             # push(temp);
	popw %ax
	movw %di, [%esi+%eax] 		# cpu->pc=*(uint16_t*)&(cpu->mem[0xfffe]);
	or byte ptr [%ebp+10], 4 	# cpu->p |= FLAG_I;
	movw [%ebp+4],%di            # Remove when C-only
	movb [%ebp+9],%ch    	    # Remove when C-only
	pop %eax
	add %eax,7		            # c += 7;
	push %eax
no_interrupt:
    and dword ptr [%ebp+12], 0xFFFFFF7f   # cpu->interrupt &= ~128

	# Update CPU->cycles
	pop %eax
	sub [%ebp+24], %eax

	# Update intermediate cycle counter
	C_CALL_PRE
	push %eax
	push %ebp
 	call do_poll
 	add %esp,8
 	C_CALL_POST

	mov %eax, [%ebp+24]

	# Loop back until cpu->cycles <=0
	cmp dword ptr [%ebp+24], 0
	jg loop

	# Update cpu fields, restore regs and exit
    movw [%ebp+4], %di 	#  PC
    movb [%ebp+6], %cl	#  r7 = A
    movb [%ebp+7], %dl	#  r8 = X
    movb [%ebp+8], %dh	#  r9 = Y
    movb [%ebp+9], %ch	# r10 = S
	popa
    ret


// branch without labels: count number of lines to skip over, add 1, multiply by 4. So to skip 2 lines, "b .+12"

/**********
 * Macros *
 **********/

// NB: Macro return/destination values are always in eax. Macro params are always inputs.

.macro READBYTE addr16       # #define readmem(x)  ((x<0xfe00) ? cpu->mem[x] : readmem_ex(x))
	cmp \addr16, 0xfe00
	jae 1f
	movzx %eax, \addr16
	movzx %eax, byte ptr [%esi + %eax]
	jmp 2f
1:
	C_CALL_PRE
	pushw \addr16
	call readmem_ex
	add %esp,2
	C_CALL_POST
2:
.endm

.macro READBYTE_PC
	READBYTE %di
	inc %di
.endm

//#define readword(x) ((x<0xfe00) ? (*((uint16_t*)&(cpu->mem[x]))) : (readmem_ex(x) | (readmem_ex(x+1)<<8)))
.macro READWORD src
	cmp \src, 0xfe00
	jae 1f
	movzx %eax, \src
	movzx %eax, word ptr [%esi + %eax]
	jmp 2f
1:
	C_CALL_PRE
	pushw \src
	call readword_ex
	add %esp,2
	C_CALL_POST
2:
.endm

.macro READWORD_PC
	READWORD %di
	add %di, 2
.endm

.macro WRITEBYTED val, addr
    movzx %ebx,\addr
	movb [%esi + %ebx], \val
.endm
.macro WRITEBYTE val, addr
    movzx %ebx,\addr
	cmp %ebx,0x8000
	jae 1f
	LOG_WRITEBYTE \val, %bx
	movb [%esi + %ebx], \val
	jmp 2f
1:
	C_CALL_PRE
	movzx %eax,\val
	push %ax
	push %bx
	call writemem_ex
	add %esp, 4
	C_CALL_POST
2:
.endm

.macro RETURN clocks
	mov %eax, \clocks
	ret
.endm



// #define setzn(v) if(v) cpu->p &=~FLAG_Z; else cpu->p|=FLAG_Z;  if ((v)&0x80) cpu->p|=FLAG_N; else cpu->p&=~FLAG_N;
.macro SETZN v
	and byte ptr [%ebp+10], 0x7D  # i.e. clear flags reg bits 0x80 (N) and 0x02 (Z).
	cmp \v, 0
	jne 1f
	or byte ptr [%ebp+10], 0x02   # set Z flag
1:
	movzx %ax, \v
    bt %ax, 7
    jnc 2f
	or byte ptr [%ebp+10], 0x80   # set N flag
2:
.endm



/**********************************
 * Immediate and Zero-page macros *
 **********************************/

// On exit, EAX holds the byte read and EBX holds the address it was read from

.macro START_ZP
	READBYTE_PC		        # uint16_t addr=readmem(cpu->pc); cpu->pc++;
	mov %ebx, %eax
	movzx %eax, byte ptr [%esi + %ebx] # uint8_t temp=readmem(addr);
.endm

.macro START_ZP_X
	READBYTE_PC			    # uint16_t addr=readmem(cpu->pc); cpu->pc++;
	add %al, %dl
	mov %ebx, %eax
	movzx %eax, byte ptr [%esi + %ebx] # uint8_t temp=readmem(addr);
.endm

.macro START_ABS
	READWORD_PC			    # uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	mov %bx,%ax
	READBYTE %bx		    # uint8_t temp=readmem(addr);
.endm

.macro START_ABS_X
	READWORD_PC 			# uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	movzx %bx,%dl
	add %bx, %ax
	READBYTE %bx		    # uint8_t temp=readmem(addr);
.endm

.macro START_ABS_Y
	READWORD_PC 			# uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	movzx %bx,%dh
	add %bx, %ax
	READBYTE %bx		    # uint8_t temp=readmem(addr);
.endm


// For instructions of the form OPCODE (,x)
.macro START_INDZX
	READBYTE_PC 			# register uint8_t temp=readmem(cpu->pc); pc++;
    movzx %bx, %dl
	add %bx, %ax			# temp += cpu->x;
	and %bx, 255
	READWORD %bx		    # register uint16_t addr=readword(temp);
.endm

// For instructions of the form OPCODE (),y
.macro START_INDZY
	READBYTE_PC			    # register uint8_t temp=readmem(cpu->pc); pc++;
	READWORD %ax    		# register uint16_t addr=readword(temp);
    movzx %bx, %dh
	add %bx, %ax			# addr += cpu->y;
	and %bx, 255
	READBYTE %bx		    # temp = readmem(addr);
.endm




/*************************************/
/**  OPCODES IN ALPHABETICAL ORDER  **
/*************************************/


/*******
 * ADC *
 *******/

// branch without labels: count number of lines to skip over, add 1, multiply by 4. So to skip 2 lines, "b .+12"

// going backwards, divide by 4, sub 1, thats the number of lines to skip. so +80 = 19 lines

.macro BODY_ADC
    push %ebx
    movzx %ebx, byte ptr [%ebp+10]
	bt %bx,3
	jc 3f
	bt %bx,0
	adc %cl, %al
	pushf
	pop %eax
	and %bl, 0xBE # clear C and V
	bt %ax,0
	jnc 1f
	or %bl,1    # set C
1:
    bt %ax,11
    jnc 2f
	or %bx, 0x40    # set V
2:
    mov byte ptr [%ebp+10], %bl
	SETZN %cl				# setzn(cpu->a);
	jmp 4f
3:
	movb [%ebp+6], %cl	    # update A
	C_CALL_PRE
	push %eax
	push %ebp   			# r0=cpu*, r1=imm
	call adc_bcd
	add %esp, 8
	C_CALL_POST
	movb %cl, [%ebp+6]	    # update A
4:
    pop %ebx
.endm

// 0x69: ADC imm
opasm_adc_imm:
	READBYTE_PC		        # register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	BODY_ADC
	RETURN 2

// 0x65: ADC zp
opasm_adc_zp:
	START_ZP				# r0=zp address, r1=byte from zp address
	BODY_ADC
	RETURN 3				# return 3;

// 0x75: ADC zp,x
opasm_adc_zp_x:
	START_ZP_X				# r0=zp address, r1=byte from zp address
	BODY_ADC
	RETURN 3				# return 3;

// 0x6D: ADC abs
opasm_adc_abs:
	START_ABS				# r0=address, r1=byte from address
	BODY_ADC
	RETURN 4				# return 4

// 0x79: ADC abs,y
opasm_adc_abs_y:
	START_ABS_Y				# r0=address, r1=byte from address
	BODY_ADC
	RETURN 4				# return 4

// 0x7D: ADC abs,x
opasm_adc_abs_x:
	START_ABS_X				# r0=address, r1=byte from address
	BODY_ADC
	RETURN 4				# return 4

// 0x61: ADC (,x)
opasm_adc_indzx:
	START_INDZX
	READBYTE %ax       		# temp = readmem(addr);
	BODY_ADC
	RETURN 6				# return 6

// 0x71: ADC (),y
opasm_adc_indzy:
	START_INDZY
	BODY_ADC
	RETURN 5				# return 5


/*******
 * AND *
 *******/
.macro BODY_AND
	and %cl, %al		    # cpu->a &= temp;
	SETZN %cl				# setzn(cpu->a);
.endm

// 0x21: AND (,x)
opasm_and_indzx:
	START_INDZX
	READBYTE %ax		    # temp = readmem(addr);
	BODY_AND
	RETURN 6

// 0x31: AND (),y
opasm_and_indzy:
	START_INDZY
	BODY_AND
	RETURN 5

// 0x29: AND imm
opasm_and_imm:
	READBYTE_PC			    # al = imm
	BODY_AND
	RETURN 3

// 0x25: AND zp
opasm_and_zp:
	START_ZP
	BODY_AND
	RETURN 3

// 0x35: AND zp,x
opasm_and_zp_x:
	START_ZP_X
	BODY_AND
	RETURN 3

// 0x2D: AND abs
opasm_and_abs:
	START_ABS
	BODY_AND
	RETURN 4

// 0x39: AND abs,y
opasm_and_abs_y:
	START_ABS_Y
	BODY_AND
	RETURN 4

// 0x3D: AND abs,x
opasm_and_abs_x:
	START_ABS_X
	BODY_AND
	RETURN 4


/*******
 * ASL *
 *******/

.macro BODY_ASL reg8
	and byte ptr [%ebp+10], 0xfe	# clear FLAG_C
	shl \reg8, 1			# temp<<=1;
	jnc 1f
	or byte ptr [%ebp+10], 1
1:
	SETZN \reg8				# setzn(temp);
.endm

opasm_asl_a:
	BODY_ASL %cl
	RETURN 2

// 0x06: ASL zp
opasm_asl_zp:
	START_ZP				# ebx=zp address, eax=byte from zp address
	BODY_ASL %al
	WRITEBYTED %al,%bx
	RETURN 5

// 0x16: ASL zp,x
opasm_asl_zp_x:
	START_ZP_X				# r1=byte from zp, r0=zp address
	BODY_ASL %al
	WRITEBYTED %al,%bx
	RETURN 5

// 0x0E: ASL abs
opasm_asl_abs:
	START_ABS
	BODY_ASL %al
	WRITEBYTE %al,%bx
	RETURN 6

// 0x1E: ASL abs,x
opasm_asl_abs_x:
	START_ABS_X
	BODY_ASL %al
	WRITEBYTE %al,%bx
	RETURN 7


/*************************************************
 * BNE / BEQ / BPL / BMI / BVC / BVS / BCC / BCS *
 *************************************************/

.macro BRANCH flag, testop
	READBYTE %di		    # register int8_t offset=(int8_t)readmem(cpu->pc);
	movsx %eax, %al         # to get the byte offset into a signed word
	inc %di 				# pc++;
	test byte ptr [%ebp+10], \flag # if (!(cpu->p & FLAG_Z)) {
	\testop 1f
	add %di, %ax			#     cpu->pc+=offset;
	RETURN 3				# }
1:
    RETURN 2
.endm

opasm_bne:
	BRANCH 2, jne
opasm_beq:
	BRANCH 2, je
opasm_bpl:
	BRANCH 0x80, jne
opasm_bmi:
	BRANCH 0x80, je
opasm_bvc:
	BRANCH 0x40, jne
opasm_bvs:
	BRANCH 0x40, je
opasm_bcc:
	BRANCH 1, jne
opasm_bcs:
	BRANCH 1, je



/*******
 * BIT *
 *******/

.macro BODY_BIT
    mov %bl, [%ebp+10]
	and %bl, 0x3d		# clear Z,V,N
	bt %ax, 6			    # SET_FLAG(FLAG_V, temp&0x40);
	jnc 1f
	or %bl, 0x40
1:
	bt %ax, 7			    # SET_FLAG(FLAG_N, temp&0x80);
	jnc 2f
	or %bl, 0x80
2:
	and %al, %cl		    # temp = cpu->a&temp
	jnz 3f                  # SET_FLAG(FLAG_Z, !temp)
	or %bl, 2
3:
    mov [%ebp+10], %bl      # save P
.endm

// 0x24: BIT zp
opasm_bit_zp:
	START_ZP
	BODY_BIT
	RETURN 3

// 0x2C: BIT abs
opasm_bit_abs:
	START_ABS
	BODY_BIT
	RETURN 4


/*************************
 * CLC / CLD / CLV / CLI *
 *************************/
opasm_clc:
	and byte ptr [%ebp+10], 0xFE 	# cpu->p &= ~FLAG_C;
	RETURN 2
opasm_cld:
	and byte ptr [%ebp+10], 0xF7 	# cpu->p &= ~FLAG_D;
	RETURN 2
opasm_clv:
	and byte ptr [%ebp+10], 0xBF 	# cpu->p &= ~FLAG_V;
	RETURN 2
opasm_cli:
	UPDATE_TAKEINT			        # cpu->takeint=(cpu->interrupt && !(cpu->p & FLAG_I));
	and byte ptr [%ebp+10], 0xFB	# cpu->p &= ~FLAG_I;
	RETURN -2


/*******
 * CMP *
 *******/
.macro BODY_CMP reg8
	and byte ptr [%ebp+10], 0xFE    # clear FLAG_C
	mov %ah, \reg8
	sub %ah, %al                    #ah = cpureg - temp
    jc 1f
	or byte ptr [%ebp+10], 1	    # set FLAG_C if carried
1:
	SETZN %ah				        # setzn(temp);
.endm

// 0xC9: CMP imm
opasm_cmp_imm:
	READBYTE_PC     		        # register uint8_t temp=readmem(cpu->pc); cpu->pc++;
    BODY_CMP %cl
	RETURN 2

// 0xC5: CMP zp
// 0xE4: CPX zp
// 0xC4: CPY zp
opasm_cmp_zp:
	START_ZP
	BODY_CMP %cl
    RETURN 3
opasm_cpx_zp:
	START_ZP
	BODY_CMP %dl
    RETURN 3
opasm_cpy_zp:
	START_ZP
	BODY_CMP %dh
    RETURN 3

// 0xD5: CMP zp,x
opasm_cmp_zp_x:
	START_ZP_X
	BODY_CMP %cl
    RETURN 3

// 0xCD: CMP abs
opasm_cmp_abs:
	START_ABS
	BODY_CMP %cl
    RETURN 4

// 0xD9: CMP abs,y
opasm_cmp_abs_y:
	START_ABS_Y
	BODY_CMP %cl
    RETURN 4

// 0xDD: CMP abs,x
opasm_cmp_abs_x:
	START_ABS_X
	BODY_CMP %cl
    RETURN 4

// 0xC1: CMP (,x)
opasm_cmp_indzx:
	START_INDZX
	READBYTE %ax		# temp = readmem(addr);
	BODY_CMP %cl
    RETURN 6

// 0xD1: CMP (),y
opasm_cmp_indzy:
	START_INDZY
	BODY_CMP %cl
    RETURN 6


/*******
 * CPX *
 *******/

// 0xE0: CPX imm
opasm_cpx_imm:
	READBYTE_PC
	BODY_CMP %dl
	RETURN 2

// 0xEC: CPX abs
opasm_cpx_abs:
	START_ABS
	BODY_CMP %dl
	RETURN 4


/*******
 * CPY *
 *******/

// 0xC0: CPY imm
opasm_cpy_imm:
	READBYTE_PC
	BODY_CMP %dh
	RETURN 2

// 0xCC: CPY abs
opasm_cpy_abs:
	START_ABS
	BODY_CMP %dh
	RETURN 4


/*******
 * DEC *
 *******/

.macro BODY_DEC
	dec %al
	SETZN %al
.endm

// 0xC6: DEC zp
opasm_dec_zp:
	START_ZP
	BODY_DEC
	WRITEBYTED %al,%bx
	RETURN 5

// 0xD6: DEC zp,x
opasm_dec_zp_x:
	START_ZP_X
	BODY_DEC
	WRITEBYTED %al,%bx
	RETURN 5

// 0xCE: DEC abs
opasm_dec_abs:
	START_ABS
	BODY_DEC
	WRITEBYTE %al,%bx
	RETURN 6

// 0xDE: DEC abs,x
opasm_dec_abs_x:
	START_ABS_X
	BODY_DEC
	WRITEBYTE %al,%bx
	RETURN 7


/*************
 * DEX / DEY *
 *************/

// 0xCA: DEX
opasm_dex:
    dec %dl
	SETZN %dl
	RETURN 2

// 0x88: DEY
opasm_dey:
    dec %dh
	SETZN %dh
	RETURN 2


/*******
 * EOR *
 *******/

.macro BODY_EOR
	xor %cl, %al		# cpu->a ^= temp;
	SETZN %cl
.endm

// 0x49: EOR imm
opasm_eor_imm:
	READBYTE_PC
	BODY_EOR
	RETURN 2

// 0x45: EOR zp
opasm_eor_zp:
	START_ZP
	BODY_EOR
	RETURN 3

// 0x55: EOR zp,x
opasm_eor_zp_x:
	START_ZP_X
	BODY_EOR
	RETURN 3

// 0x4D: EOR abs
opasm_eor_abs:
	START_ABS
	BODY_EOR
	RETURN 4

// 0x59: EOR abs,y
opasm_eor_abs_y:
	START_ABS_Y
	BODY_EOR
	RETURN 4

// 0x5D: EOR abs,x
opasm_eor_abs_x:
	START_ABS_X
	BODY_EOR
	RETURN 4

// 0x41: EOR (,x)
opasm_eor_indzx:
	START_INDZX
	READBYTE %ax
	BODY_EOR
	RETURN 6

// 0x51: EOR (),y
opasm_eor_indzy:
	START_INDZY
	BODY_EOR
	RETURN 5


/*******
 * INC *
 *******/

.macro BODY_INC
	inc %al
	SETZN %al
.endm

// 0xE6: INC zp
opasm_inc_zp:
	START_ZP
	BODY_INC
	WRITEBYTED %al,%bx
	RETURN 5

// 0xF6: INC zp,x
opasm_inc_zp_x:
	START_ZP_X
	BODY_INC
	WRITEBYTED %al,%bx
	RETURN 5


// 0xEE: INC abs
opasm_inc_abs:
	START_ABS
	BODY_INC
    WRITEBYTE %al,%bx
	RETURN 6


// 0xFE: INC abs,x
opasm_inc_abs_x:
	START_ABS_X
	BODY_INC
    WRITEBYTE %al,%bx
	RETURN 7


/*************
 * INX / INY *
 *************/

// 0xE8: INX
opasm_inx:
    inc %dl
	SETZN %dl
	RETURN 2

// 0xC8: INY
opasm_iny:
    inc %dh
	SETZN %dh
	RETURN 2


/*******
 * JMP *
 *******/

// 0x4C: JMP
opasm_jmp:
	READWORD %di
    mov %di, %ax
	RETURN 3

// 0x6C: JMP ()
opasm_jmp_ind:
	READWORD_PC			    # register uint16_t addr=readwordpc();
	cmp %al, 255
	je 1f
	READWORD %ax    		# cpu->pc=readword(addr);
	mov %di, %ax
	RETURN 5
1:
	// JMP address lives in word spanning page boundary... have to do a silly thing.
	push %ecx
	mov %bx, %ax
	READBYTE %bx		    # get lo byte from top of page...
	mov %cl, %al
	and %bx, 0xff00		    # and hi byte from bottom of page!
	READBYTE %bx
	mov %ch, %al
	mov %di, %cx
	pop %ecx
	RETURN 5


/*******
 * JSR *
 *******/

opasm_jsr:
	READWORD %di		    # uint16_t addr=readwordpc(); cpu->pc--;
	mov %bx, %ax
	inc %di
	PUSHWORD %di
	mov %di, %bx		    # cpu->pc=addr;
	RETURN 6


/*******
 * LAX *
 *******/

// 0xB7: LAX zp,y
opasm_lax_zp_y:
	READBYTE_PC			    # uint16_t addr=readmem(cpu->pc); cpu->pc++;
	add %al, %dh   	        # addr += cpu->y
	READBYTE %ax   	        # cpu->a=cpu->x=readmem(addr);
	mov %cl, %al
	mov %dl, %al
	SETZN %cl				# setzn(cpu->a);
	RETURN 3


/*******************
 * LDA / LDX / LDY *
 *******************/

// 0xA9: LDA imm
// 0xA2: LDX imm
// 0xA0: LDY imm
.macro LOAD_IMM dest
	READBYTE_PC 	    # cpu->a = readmem(cpu->pc); pc++;
	mov \dest, %al
	SETZN \dest			# setzn(cpu->a);
	RETURN 2
.endm
opasm_lda_imm:
	LOAD_IMM %cl
opasm_ldx_imm:
	LOAD_IMM %dl
opasm_ldy_imm:
	LOAD_IMM %dh

// 0xA5: LDA zp
// 0xA6: LDX zp
// 0xA4: LDY zp
.macro LOAD_ZP dest
	READBYTE_PC 		# uint16_t addr=readmem(cpu->pc); cpu->pc++;
	READBYTE %ax	    # cpu->a=readmem(addr);
	mov \dest, %al
	SETZN \dest			# setzn(cpu->a);
	RETURN 3
.endm
opasm_lda_zp:
	LOAD_ZP %cl
opasm_ldx_zp:
	LOAD_ZP %dl
opasm_ldy_zp:
	LOAD_ZP %dh

// 0xB5: LDA zp,x
// 0xB6: LDX zp,y
// 0xB4: LDY zp,x
.macro LOAD_ZPO dest, offset
	READBYTE_PC			    # uint16_t addr=readmem(cpu->pc); cpu->pc++;
	add %al, \offset         # addr += cpu->x
	READBYTE %ax   	        # cpu->a=readmem(addr);
	mov \dest, %al
	SETZN \dest				# setzn(cpu->a);
	RETURN 3
.endm
opasm_lda_zp_x:
	LOAD_ZPO %cl, %dl
opasm_ldx_zp_y:
	LOAD_ZPO %dl, %dh
opasm_ldy_zp_x:
	LOAD_ZPO %dh, %dl

// 0xAC: LDY abs
// 0xAD: LDA abs
// 0xAE: LDX abs
.macro LOAD_ABS dest
	READWORD_PC			    # uint16_t addr=readwordpc();
	READBYTE %ax   	        # cpu->a=readmem(addr);
	mov \dest, %al
	SETZN \dest				# setzn(cpu->a);
    RETURN 4
.endm
opasm_lda_abs:
	LOAD_ABS %cl
opasm_ldx_abs:
	LOAD_ABS %dl
opasm_ldy_abs:
	LOAD_ABS %dh

// 0xBD: LDA abs,x
// 0xB9: LDA abs,y
// 0xBE: LDX abs,y
// 0xBC: LDY abs,x
.macro LOAD_ABS_OFF dest, offs
	READWORD_PC			    # uint16_t addr=readwordpc();
	add %al, \offs
	adc %ah, 0
	READBYTE %ax   	        # cpu->a=readmem(addr);
	mov \dest, %al
	SETZN \dest				# setzn(cpu->a);
	RETURN 4
.endm
opasm_lda_abs_x:
	LOAD_ABS_OFF %cl, %dl
opasm_lda_abs_y:
	LOAD_ABS_OFF %cl, %dh
opasm_ldx_abs_y:
	LOAD_ABS_OFF %dl, %dh
opasm_ldy_abs_x:
	LOAD_ABS_OFF %dh, %dl

// 0xA1: LDA (,x)
opasm_lda_indzx:
	START_INDZX
	READBYTE %ax		    # cpu->a = readmem(addr);
	mov %cl, %al
	SETZN %cl
	RETURN 6

// 0xB1: LDA (),y
opasm_lda_y:
	READBYTE_PC			    # register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	READWORD %ax 		    # register uint16_t addr=readword(temp);
	add %al, %dh		    # addr += y
	adc %ah, 0
	READBYTE %ax		    # cpu->a=readmem(addr);
	mov %cl, %al
	SETZN %cl				# setzn(cpu->a);
	RETURN 5


/*******
 * LSR *
 *******/

.macro BODY_LSR reg8
	and byte ptr [%ebp+10], 0xfe	# clear FLAG_C
	shr \reg8
	jnc 1f
	or byte ptr [%ebp+10], 1
1:
	SETZN \reg8				# setzn(temp);
.endm

// 0x4A: LSR A
opasm_lsr_a:
	BODY_LSR %cl
	RETURN 2

// 0x46: LSR zp
opasm_lsr_zp:
	START_ZP
	BODY_LSR %al
	WRITEBYTED %al, %bx
	RETURN 5

// 0x56: LSR zp,x
opasm_lsr_zp_x:
	START_ZP_X
	BODY_LSR %al
	WRITEBYTED %al, %bx
	RETURN 5


// 0x4E: LSR abs
opasm_lsr_abs:
	START_ABS
	BODY_LSR %al
	WRITEBYTE %al, %bx
	RETURN 6

// 0x5E: LSR abs,x
opasm_lsr_abs_x:
	START_ABS_X
	BODY_LSR %al
	WRITEBYTE %al, %bx
	RETURN 7


/*******
 * NOP *
 *******/

// 0xEA: NOP
opasm_nop:
	RETURN 2

// 0x80: NOP imm		undocumented
// 0x89: NOP imm		undocumented
// 0xC2: NOP imm		undocumented
// 0xE2: NOP imm		undocumented
opasm_nop_imm:
	RETURN 2

// 0x04: NOP zp			undocumented
opasm_nop_zp:
	START_ZP
	RETURN 3

// 0x14: NOP zp,x		undocumented
opasm_nop_zp_x:
	START_ZP_X
	RETURN 3

// 0x0C: NOP abs		undocumented
opasm_nop_abs:
	START_ABS
	RETURN 4

// 0xFC: NOP abs,x			undocumented
opasm_nop_abs_x:
	START_ABS_X
	RETURN 4


/*******
 * ORA *
 *******/

.macro BODY_ORA
	or %cl, %al		        # cpu->a|=readmem(addr);
	SETZN %cl				# setzn(cpu->a);
.endm

// 0x09: ORA imm
opasm_ora_imm:
	READBYTE_PC
	BODY_ORA
	RETURN 2

// 0x05: ORA zp
opasm_ora_zp:
	START_ZP
	BODY_ORA
	RETURN 3

// 0x15: ORA zp,x
opasm_ora_zp_x:
	START_ZP_X
	BODY_ORA
	RETURN 3

// 0x0D: ORA abs
opasm_ora_abs:
	START_ABS
	BODY_ORA
	RETURN 4

// 0x19: ORA abs,y
opasm_ora_abs_y:
	START_ABS_Y
	BODY_ORA
	RETURN 4

// 0x1D: ORA abs,x
opasm_ora_abs_x:
	START_ABS_X
	BODY_ORA
	RETURN 4

// 0x01: ORA (,x)
opasm_ora_indzx:
	START_INDZX
	READBYTE %ax
	BODY_ORA
	RETURN 6

// 0x11: ORA (),y
opasm_ora_indzy:
	START_INDZY
	BODY_ORA
	RETURN 5


/*******
 * PHA *
 *******/

// 0x48: PHA
opasm_pha:
	PUSH_BYTE %cl			# push(cpu->a);
	RETURN 3


/*******
 * PHP *
 *******/

// 0x08: PHP
opasm_php:
    movb %al, [%ebp+10]
	or %al, 0x30 	        # register uint8_t  temp=0x30 | cpu->p;
	PUSH_BYTE %al	 		# push(temp);
	RETURN 3


/*******
 * PLA *
 *******/

// 0x68: PLA
opasm_pla:
	PULLBYTE 			    # cpu->a = pull();
	mov %cl, %al
	SETZN %cl				# setzn(cpu->a);
	RETURN 4


/*******
 * PLP *
 *******/

// 0x28: PLP
opasm_plp:
	PULLBYTE    			# uint8_t temp=pull();
	//TODO! cpu->takeint=(cpu->interrupt && !(cpu->p & FLAG_I));
	and %al, 0xCF	        # cpu->p = temp & ~0x30;
	mov [%ebp+10], %al
	RETURN -4


/********************************
 * RLA : Undocumented ROL + AND *
 ********************************/
 .macro BODY_RLA
    push %ebx
    movb %bl, byte ptr [%ebp+10]
    bt %bx, 0                        # get FLAG_C into x86 carry flag
    rcl %al
    jc 1f
	and %bl, 0xfe    # no carry from the rcl, so clear FLAG_C
	jmp 2f
1:
	or %bl, 1        # set FLAG_C
2:
    movb [%ebp+10], %bl
    pop %ebx
	WRITEBYTED %al, %bx
	and %cl, %al                    # AND with A
	SETZN %al
.endm
opasm_rla_zp:
	START_ZP
	BODY_RLA
	RETURN 5
opasm_rla_zp_x:
	START_ZP_X
	BODY_RLA
	RETURN 5
opasm_rla_abs:
	START_ABS
	BODY_RLA
	RETURN 6
opasm_rla_abs_x:
	START_ABS_X
	BODY_RLA
	RETURN 6
opasm_rla_abs_y:
	START_ABS_Y
	BODY_RLA
	RETURN 6
opasm_rla_indzx:
	START_INDZX
	READBYTE %ax
	BODY_RLA
	RETURN 7
opasm_rla_indzy:
	START_INDZY
	BODY_RLA
	RETURN 7


/*******
 * ROL *
 *******/
 .macro BODY_ROL reg
    push %ebx
    movb %bl, byte ptr [%ebp+10]
    bt %bx, 0                        # get FLAG_C into x86 carry flag
    rcl \reg
    jc 1f
	and %bl, 0xfe                   # no carry from the rcl, so clear FLAG_C
	jmp 2f
1:
	or %bl, 1        # set FLAG_C
2:
    movb [%ebp+10], %bl
    pop %ebx
	SETZN \reg
.endm

// 0x2A: ROL A
opasm_rol_a:
	BODY_ROL %cl
	RETURN 2

// 0x26: ROL zp
opasm_rol_zp:
	START_ZP
	BODY_ROL %al
	WRITEBYTED %al, %bx
	RETURN 3

// 0x36: ROL zp,x
opasm_rol_zp_x:
	START_ZP_X
	BODY_ROL %al
	WRITEBYTED %al, %bx
	RETURN 5

// 0x2E: ROL abs
opasm_rol_abs:
	START_ABS
	BODY_ROL %al
	WRITEBYTE %al, %bx
	RETURN 6

// 0x3E: ROL abs,x
opasm_rol_abs_x:
	START_ABS_X
	BODY_ROL %al
	WRITEBYTE %al, %bx
	RETURN 6


/*******
 * ROR *
 *******/
 .macro BODY_ROR reg
    push %ebx
    movb %bl, byte ptr [%ebp+10]
    bt %bx, 0                        # get FLAG_C into x86 carry flag
    rcr \reg
    jc 1f
	and %bl, 0xfe                   # no carry, so clear FLAG_C
	jmp 2f
1:
	or %bl, 1        # set FLAG_C
2:
    movb [%ebp+10], %bl
    pop %ebx
	SETZN \reg
.endm

// 0x6A: ROR A
opasm_ror_a:
	BODY_ROR %cl
	RETURN 2

// 0x66: ROR zp
opasm_ror_zp:
	START_ZP
	BODY_ROR %al
	WRITEBYTED %al, %bx
	RETURN 5

// 0x76: ROR zp,x
opasm_ror_zp_x:
	START_ZP_X
	BODY_ROR %al
	WRITEBYTED %al, %bx
	RETURN 5

// 0x6E: ROR abs
opasm_ror_abs:
	START_ABS
	BODY_ROR %al
	WRITEBYTE %al, %bx
	RETURN 6

// 0x7E: ROR abs,x
opasm_ror_abs_x:
	START_ABS_X
	BODY_ROR %al
	WRITEBYTE %al, %bx
	RETURN 7


/*******
 * RRA *
 *******/

.macro BODY_RRA
    push %ebx
    movb %bl, byte ptr [%ebp+10]
    bt %bx, 0                        # get FLAG_C into x86 carry flag
    rcr %al
    jc 1f
	and %bl, 0xfe    # no carry, so clear FLAG_C
	jmp 2f
1:
	or %bl, 1        # set FLAG_C
2:
    movb [%ebp+10], %bl
    pop %ebx
	WRITEBYTED %al, %bx
	BODY_ADC
.endm


// 0x67: RRA zp			undocumented
opasm_rra_zp:
	START_ZP
	BODY_RRA
	RETURN 5

// 0x77: RRA zp,x		undocumented
opasm_rra_zp_x:
	START_ZP_X
	BODY_RRA
	RETURN 5

// 0x6F: RRA abs		undocumented
opasm_rra_abs:
	START_ABS
	BODY_RRA
	RETURN 6

// 0x7B: RRA abs,y		undocumented
opasm_rra_abs_y:
	START_ABS_Y
	BODY_RRA
	RETURN 6

// 0x7F: RRA abs,x		undocumented
opasm_rra_abs_x:
	START_ABS_X
	BODY_RRA
	RETURN 6

// 0x63: RRA (,x)		undocumented
opasm_rra_indzx:
	START_INDZX
	READBYTE %ax
	BODY_RRA
	RETURN 7

// 0x73: RRA (),y		undocumented
opasm_rra_indzy:
	START_INDZY
	BODY_RRA
	RETURN 7

/*******
 * RTI *
 *******/

// 0x40: RTI
opasm_rti:
	PULLBYTE			    # uint8_t temp=pull();
	and %al, 0xcf 		    # cpu->p=temp & ~0x30;
	movb [%ebp+10], %al
	PULLWORD %di			# cpu->pc=pull(); cpu->pc|=(pull()<<8);
	RETURN 6


/*******
 * RTS *
 *******/

// 0x60: RTS
opasm_rts:
	PULLWORD %di			# cpu->pc=pull(); cpu->pc|=(pull()<<8);
	inc %di				    # cpu->pc++;
	RETURN 6



/*******
 * SBC *
 *******/

.macro BODY_SBC
    push %ebx               # if (cpu->p & FLAG_D) do BCD arithmetic in a C function
    movzx %ebx, byte ptr [%ebp+10]
	bt %bx,3
	jc 3f
    bt %bx, 0               # get FLAG_C into x86 carry flag
    sbb %cl, %al            # x86 SBB  works exact same way as 6502 SBC
	pushf
	pop %eax
	and %bl, 0xBE           # clear C and V
	bt %ax,0
	jnc 1f
	or %bl,1                # set C
1:
    bt %ax,11
    jnc 2f
	or %bx, 0x40           # set V
2:
    mov byte ptr [%ebp+10], %bl
	SETZN %cl				# setzn(cpu->a);
	jmp 4f
3:
	movb [%ebp+6], %cl	    # update A
	C_CALL_PRE
	push %eax
	push %ebp
	call sbc_bcd
	add %esp, 8
	C_CALL_POST
	movb %cl,  [%ebp+6]     # update A
4:
    pop %ebx
.endm

// 0xE9: SBC imm
// 0xEB: SBC imm
opasm_sbc_imm:
	READBYTE_PC     		# register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	BODY_SBC
	RETURN 2

// 0xE5: SBC zp
opasm_sbc_zp:
	START_ZP				# r0=zp address, r1=byte from zp address
	BODY_SBC
	RETURN 3

// 0xF5: SBC zp,x
opasm_sbc_zp_x:
	START_ZP_X
	BODY_SBC
	RETURN 3

// 0xED: SBC abs
opasm_sbc_abs:
	START_ABS
	BODY_SBC
	RETURN 4

// 0xF9: SBC abs,y
opasm_sbc_abs_y:
	START_ABS_Y
	BODY_SBC
	RETURN 4

// 0xFD: SBC abs,x
opasm_sbc_abs_x:
	START_ABS_X
	BODY_SBC
	RETURN 4

// 0xE1: SBC (,x)
opasm_sbc_indzx:
	START_INDZX
	READBYTE %ax
	BODY_SBC
	RETURN 6

// 0xF1: SBC (),y
opasm_sbc_indzy:
	START_INDZY
	BODY_SBC
	RETURN 5


/*******************
 * SEC / SED / SEI *
 *******************/

opasm_sec:
	or byte ptr [%ebp+10], 1	# cpu->p |= FLAG_C;
	RETURN 2
opasm_sed:
	or byte ptr [%ebp+10], 8	# cpu->p |= FLAG_D;
	RETURN 2
opasm_sei:
	UPDATE_TAKEINT			# cpu->takeint=(cpu->interrupt && !(cpu->p & FLAG_I));
	or byte ptr [%ebp+10], 4			# cpu->p |= FLAG_I;
	RETURN -2


/**************************************
 * SLO - Undocumented Shift Left & Or *
 **************************************/

.macro BODY_SLO
	shl %al				    # temp<<=1;
	WRITEBYTE %al, %bx		# writemem(addr,temp);
	or %cl, %al		        # cpu->a|=temp;
	SETZN %cl				# setzn(cpu->a);
.endm
opasm_slo_zp:
	START_ZP
	BODY_SLO
	RETURN 5
opasm_slo_zp_x:
	START_ZP_X
	BODY_SLO
	RETURN 5
opasm_slo_abs:
	START_ABS
	BODY_SLO
	RETURN 4
opasm_slo_abs_y:
	START_ABS_Y
	BODY_SLO
	RETURN 6
opasm_slo_abs_x:
	START_ABS_X
	BODY_SLO
	RETURN 6
opasm_slo_indzx:
	START_INDZX
	READBYTE %ax
	BODY_SLO
	RETURN 7
opasm_slo_indzy:
	START_INDZY
	BODY_SLO
	RETURN 7


/*******************
 * STA / STX / STY *
 *******************/

// 0x8C: STY abs
opasm_sty_abs:
	READWORD_PC			    # uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	WRITEBYTE %dh, %ax		# writemem(addr, cpu->y);
	RETURN 4

// 0x85: STA zp
// 0x84: STY zp
// 0x86: STX zp
.macro STORE_ZP reg8
	READBYTE_PC			        # uint16_t addr=readmem(cpu->pc); cpu->pc++;
	WRITEBYTE \reg8, %ax		# writemem(addr,cpu->a);
	RETURN 3
.endm
opasm_sta_zp:
	STORE_ZP %cl
opasm_stx_zp:
	STORE_ZP %dl
opasm_sty_zp:
	STORE_ZP %dh


// 0x95: STA zp,x
// 0x96: STX zp,y
// 0x94: STY zp,x
.macro STORE_ZPO val, offset
	READBYTE_PC 			# uint16_t addr=readmem(cpu->pc); cpu->pc++;
	add %al, \offset        # addr += cpu->x
	WRITEBYTE \val, %ax   	# writemem(addr, a);
	RETURN 4
.endm
opasm_sta_zp_x:
	STORE_ZPO %cl, %dl
opasm_stx_zp_y:
	STORE_ZPO %dl, %dh
opasm_sty_zp_x:
	STORE_ZPO %dh, %dl

// 0x8D: STA abs
opasm_sta_abs:
	READWORD_PC			    # uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	WRITEBYTE %cl, %ax		# writemem(addr, cpu->a);
	RETURN 4

// 0x8E: STX abs
opasm_stx_abs:
	READWORD_PC;
	WRITEBYTE %dl, %ax
	RETURN 4

// 0x99: STA abs,y
opasm_sta_abs_y:
	READWORD_PC             # uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	add %al, %dh			# addr += cpu->y;
	adc %ah, 0
	WRITEBYTE %cl, %ax		# writemem(addr, cpu->a);
	RETURN 5

// 0x9D: STA abs,x
opasm_sta_abs_x:
	READWORD_PC             # uint16_t addr=readword(cpu->pc); cpu->pc+=2;
	add %al, %dl			# addr += cpu->x;
	adc %ah, 0
	WRITEBYTE %cl, %ax		# writemem(addr, cpu->a);
	RETURN 5

// 0x81: STA (,x)
opasm_sta_indzx:
	START_INDZX
	WRITEBYTE %cl, %ax
	RETURN 6

// 0x91: STA (),y
opasm_sta_indzy:
	READBYTE_PC             # register uint8_t temp=readmem(cpu->pc); cpu->pc++;
	READWORD %ax 		    # register uint16_t addr=readword(temp);
	add %al, %dh		    # addr += y
	adc %ah, 0
	WRITEBYTE %cl, %ax		# writemem(addr, cpu->a);
	RETURN 5


/*************************************
 * TAX / TAY / TSX / TXA / TXS / TYA *
 *************************************/

// 0xAA: TAX
opasm_tax:
	mov %dl, %cl			# cpu->x = cpu->a;
	SETZN %dl				# setzn(cpu->x);
	RETURN 2

// 0xA8: TAY
opasm_tay:
	mov %dh, %cl			# cpu->y=cpu->a;
	SETZN %dh				# setzn(cpu->y);
	RETURN 2

// 0xBA: TSX
opasm_tsx:
	mov %dl, %ch			# cpu->x=cpu->s;
	SETZN %dl				# setzn(cpu->x);
	RETURN 2

// 0x8A: TXA
opasm_txa:
	mov %cl, %dl			# cpu->a=cpu->x;
	SETZN %cl				# setzn(cpu->a);
	RETURN 2

// 0x9A: TXS
opasm_txs:
	mov %ch, %dl			# cpu->s=cpu->x;
	RETURN 2

// 0x98: TYA
opasm_tya:
	mov %cl, %dh			# cpu->a=cpu->y;
	SETZN %cl				# setzn(cpu->a);
	RETURN 2


/********************
 * Undefined Opcode *
 ********************/
opasm_undef:
	C_CALL_PRE
	push %ebp
	call log_undef_opcode
	add %esp, 4
	C_CALL_POST
	dec %di                  # cpu->pc--;      	// PC never moves on
    mov dword ptr [%ebp+20], 0		# cpu->takeint=0; 	// Interrupts never occur
    mov dword ptr [%ebp+16], 0		# cpu->nmi=0;  		// NMIs never occur
    RETURN 65536			# return 100000; 	// Actually lasts forever, but the above code keeps executing HLT forever



.section .rodata
.balign 4

fns_asm:
	.long 0 				// 0x00 BRK
	.long opasm_ora_indzx 	// 0x01 ORA (,x)
	.long opasm_undef
	.long opasm_slo_indzx   // 0x03: SLO (,x)		undocumented
	.long opasm_nop_zp  	// 0x04: NOP zp			undocumented
	.long opasm_ora_zp		// 0x05: ORA zp
	.long opasm_asl_zp		// 0x06: ASL zp
	.long opasm_slo_zp  	// 0x07: SLO zp			undocumented
	.long opasm_php     	// 0x08: PHP
	.long opasm_ora_imm 	// 0x09: ORA imm
	.long opasm_asl_a   	// 0x0A: ASL A
	.long 0 				// 0x0B: ANC imm		undocumented
	.long opasm_nop_abs 	// 0x0C: NOP abs		undocumented
	.long opasm_ora_abs 	// 0x0D: ORA abs
	.long opasm_asl_abs		// 0x0E: ASL abs
	.long opasm_slo_abs 	// 0x0F: SLO abs		undocumented
	.long opasm_bpl 		// 0x10: BPL
	.long opasm_ora_indzy  	// 0x11: ORA (),y
	.long opasm_undef
	.long opasm_slo_indzy   // 0x13: SLO (),y		undocumented
	.long opasm_nop_zp_x	// 0x14: NOP zp,x		undocumented
	.long opasm_ora_zp_x	// 0x15: ORA zp,x
	.long opasm_asl_zp_x	// 0x16: ASL zp,x
	.long opasm_slo_zp_x	// 0x17: SLO zp,x		undocumented
	.long opasm_clc 		// 0x18: CLC
	.long opasm_ora_abs_y	// 0x19: ORA abs,y
	.long opasm_nop     	// 0x1A: NOP			undocumented
	.long opasm_slo_abs_y	// 0x1B: SLO abs,y		undocumented
	.long opasm_nop_abs_x	// 0x1C: NOP abs,x		undocumented
	.long opasm_ora_abs_x	// 0x1D: ORA abs,x
	.long opasm_asl_abs_x	// 0x1E: ASL abs,x
	.long opasm_slo_abs_x	// 0x1F: SLO abs,x		undocumented
	.long opasm_jsr     	// 0x20: JSR
	.long opasm_and_indzx   // 0x21: AND (,x)
	.long opasm_undef
	.long opasm_rla_indzx	// 0x23: RLA (,x)		undocumented
	.long opasm_bit_zp		// 0x24: BIT zp
	.long opasm_and_zp		// 0x25: AND zp
	.long opasm_rol_zp  	// 0x26: ROL zp
	.long opasm_rla_zp  	// 0x27: RLA zp			undocumented
	.long opasm_plp     	// 0x28: PLP
	.long opasm_and_imm		// 0x29: AND
	.long opasm_rol_a   	// 0x2A: ROL A
	.long 0 				// 0x2B: ANC imm		undocumented
	.long opasm_bit_abs		// 0x2C: BIT abs
	.long opasm_and_abs		// 0x2D: AND abs
	.long opasm_rol_abs		// 0x2E: ROL abs
	.long opasm_rla_abs 	// 0x2F: RLA abs		undocumented
	.long opasm_bmi 		// 0x30: BMI
	.long opasm_and_indzy   // 0x31: AND (),y
	.long opasm_undef
	.long opasm_rla_indzy   // 0x33: RLA (),y		undocumented
	.long opasm_nop_zp_x	// 0x34: NOP zp,x		undocumented
	.long opasm_and_zp_x	// 0x35: AND zp,x
	.long opasm_rol_zp_x	// 0x36: ROL zp,x
	.long opasm_rla_zp_x	// 0x37: RLA zp,x		undocumented
	.long opasm_sec 		// 0x38: SEC
	.long opasm_and_abs_y	// 0x39: AND abs,y
	.long opasm_nop	    	// 0x3A: NOP			undocumented
	.long opasm_rla_abs_y	// 0x3B: RLA abs,y		undocumented
	.long opasm_nop_abs_x	// 0x3C: NOP abs,x		undocumented
	.long opasm_and_abs_x	// 0x3D: AND abs,x
	.long opasm_rol_abs_x	// 0x3E: ROL abs,x
	.long opasm_rla_abs_x	// 0x3F: RLA abs,x		undocumented
	.long opasm_rti			// 0x40: RTI
	.long opasm_eor_indzx	// 0x41: EOR (,x)
	.long opasm_undef
	.long 0   				// 0x43: SRE (,x)		undocumented
	.long opasm_nop_zp  	// 0x44: NOP zp
	.long opasm_eor_zp  	// 0x45: EOR zp
	.long opasm_lsr_zp  	// 0x46: LSR zp
	.long 0  				// 0x47: SRE zp			undocumented
	.long opasm_pha     	// 0x48: PHA
	.long opasm_eor_imm 	// 0x49: EOR imm
	.long opasm_lsr_a   	// 0x4A: LSR A
	.long 0 				// 0x4B: ASR imm		undocumented
	.long opasm_jmp			// 0x4C: JMP
	.long opasm_eor_abs		// 0x4D: EOR abs
	.long opasm_lsr_abs		// 0x4E: LSR abs
	.long 0 				// 0x4F: SRE abs		undocumented
	.long opasm_bvc 		// 0x50: BVC
	.long opasm_eor_indzy	// 0x51: EOR (),y
	.long opasm_undef
	.long 0   				// 0x53: SRE (),y		undocumented
	.long opasm_nop_zp_x	// 0x54: NOP zp,x
	.long opasm_eor_zp_x	// 0x55: EOR zp,x
	.long opasm_lsr_zp_x	// 0x56: LSR zp,x
	.long 0					// 0x57: SRE zp,x		undocumented
	.long opasm_cli     	// 0x58: CLI
	.long opasm_eor_abs_y	// 0x59: EOR abs,y
	.long opasm_nop 		// 0x5A: NOP			undocumented
	.long 0					// 0x5B: SRE abs,y		undocumented
	.long opasm_nop_abs_x	// 0x5C: NOP abs,x		undocumented
	.long opasm_eor_abs_x	// 0x5D: EOR abs,x
	.long opasm_lsr_abs_x	// 0x5E: LSR abs,x
	.long 0					// 0x5F: SRE abs,x		undocumented
	.long opasm_rts 		// 0x60: RTS
	.long opasm_adc_indzx   // 0x61: ADC (,x)
	.long opasm_undef
	.long opasm_rra_indzx   // 0x63: RRA (,x)		undocumented
	.long opasm_nop_zp  	// 0x64: NOP zp			undocumented
	.long opasm_adc_zp  	// 0x65: ADC zp
	.long opasm_ror_zp		// 0x66: ROR zp
	.long opasm_rra_zp  	// 0x67: RRA zp			undocumented
	.long opasm_pla    		// 0x68: PLA
	.long opasm_adc_imm 	// 0x69: ADC imm
	.long opasm_ror_a   	// 0x6A: ROR A
	.long 0     			// 0x6B: ARR			undocumented
	.long opasm_jmp_ind		// 0x6C: JMP ()
	.long opasm_adc_abs 	// 0x6D: ADC abs
	.long opasm_ror_abs 	// 0x6E: ROR abs
	.long opasm_rra_abs 	// 0x6F: RRA abs		undocumented
	.long opasm_bvs     	// 0x70: BVS
	.long opasm_adc_indzy   // 0x71: ADC (),y
	.long opasm_undef
	.long opasm_rra_indzy  	// 0x73: RRA (),y		undocumented
	.long opasm_nop_zp_x	// 0x74: NOP zp,x		undocumented
	.long opasm_adc_zp_x 	// 0x75: ADC zp,x
	.long opasm_ror_zp_x	// 0x76: ROR zp,x
	.long opasm_rra_zp_x	// 0x77: RRA zp,x		undocumented
	.long opasm_sei     	// 0x78: SEI
	.long opasm_adc_abs_y	// 0x79: ADC abs,y
	.long opasm_nop			// 0x7A: NOP			undocumented
	.long opasm_rra_abs_y	// 0x7B: RRA abs,y		undocumented
	.long opasm_undef
	.long opasm_adc_abs_x	// 0x7D: ADC abs,x
	.long opasm_ror_abs_x	// 0x7E: ROR abs,x
	.long opasm_rra_abs_x	// 0x7F: RRA abs,x		undocumented
	.long opasm_nop_imm  	// 0x80: NOP imm		undocumented
	.long opasm_sta_indzx	// 0x81: STA (,x)
	.long opasm_nop_imm 	// 0x82: NOP imm		undocumented
	.long 0   				// 0x83: SAX (,x)		undocumented
	.long opasm_sty_zp  	// 0x84: STY zp
	.long opasm_sta_zp  	// 0x85: STA zp
	.long opasm_stx_zp  	// 0x86: STX zp
	.long 0  				// 0x87: SAX zp			undocumented
	.long opasm_dey     	// 0x88: DEY
	.long opasm_nop_imm 	// 0x89: NOP imm		undocumented
	.long opasm_txa     	// 0x8A: TXA
	.long 0     			// 0x8B: ANE			undocumented
	.long opasm_sty_abs 	// 0x8C: STY abs
	.long opasm_sta_abs 	// 0x8D: STA abs
	.long opasm_stx_abs 	// 0x8E: STX abs
	.long 0 				// 0x8F: SAX abs		undocumented
	.long opasm_bcc     	// 0x90: BCC
	.long opasm_sta_indzy	// 0x91: STA (),y
	.long opasm_undef
	.long 0   				// 0x93: SHA (),y		undocumented
	.long opasm_sty_zp_x	// 0x94: STY zp,x
	.long opasm_sta_zp_x	// 0x95: STA zp,x
	.long opasm_stx_zp_y	// 0x96: STX zp,y
	.long 0					// 0x97: SAX zp,y		undocumented
	.long opasm_tya     	// 0x98: TYA
	.long opasm_sta_abs_y	// 0x99: STA abs,y
	.long opasm_txs     	// 0x9A: TXS
	.long 0					// 0x9B: SHS abs,y		undocumented
	.long 0					// 0x9C: SHY abs,x		undocumented
	.long opasm_sta_abs_x	// 0x9D: STA abs,x
	.long 0					// 0x9E: SHX abs,y		undocumented
	.long 0					// 0x9F: SHA abs,y		undocumented
	.long opasm_ldy_imm 	// 0xA0: LDY imm
	.long opasm_lda_indzx   // 0xA1: LDA (,x)
	.long opasm_ldx_imm 	// 0xA2: LDX imm
	.long 0   				// 0xA3: LAX (,y)		undocumented
	.long opasm_ldy_zp  	// 0xA4: LDY zp
	.long opasm_lda_zp  	// 0xA5: LDA zp
	.long opasm_ldx_zp		// 0xA6: LDX zp
	.long 0  				// 0xA7: LAX zp			undocumented
	.long opasm_tay     	// 0xA8: TAY
	.long opasm_lda_imm 	// 0xA9: LDA imm
	.long opasm_tax     	// 0xAA: TAX
	.long 0     			// 0xAB: LAX			undocumented
	.long opasm_ldy_abs 	// 0xAC: LDY abs
	.long opasm_lda_abs 	// 0xAD: LDA abs
	.long opasm_ldx_abs 	// 0xAE: LDX abs
	.long 0 				// 0xAF: LAX abs
	.long opasm_bcs     	// 0xB0: BCS
	.long opasm_lda_y   	// 0xB1: LDA (),y
	.long opasm_undef
	.long 0  				// 0xB3: LAX (),y
	.long opasm_ldy_zp_x	// 0xB4: LDY zp,x
	.long opasm_lda_zp_x	// 0xB5: LDA zp,x
	.long opasm_ldx_zp_y	// 0xB6: LDX zp,y
	.long opasm_lax_zp_y	// 0xB7: LAX zp,y
	.long opasm_clv     	// 0xB8: CLV
	.long opasm_lda_abs_y	// 0xB9: LDA abs,y
	.long opasm_tsx     	// 0xBA: TSX
	.long 0					// 0xBB: LAS abs,y		undocumented
	.long opasm_ldy_abs_x	// 0xBC: LDY abs,x
	.long opasm_lda_abs_x	// 0xBD: LDA abs,x
	.long opasm_ldx_abs_y	// 0xBE: LDX abs,y
	.long 0					// 0xBF: LAX abs,y
	.long opasm_cpy_imm 	// 0xC0: CPY imm
	.long opasm_cmp_indzx	// 0xC1: CMP (,x)
	.long opasm_nop_imm 	// 0xC2: NOP imm		undocumented
	.long 0					// 0xC3: DCP (,x)		undocumented
	.long opasm_cpy_zp  	// 0xC4: CPY zp
	.long opasm_cmp_zp		// 0xC5: CMP zp
	.long opasm_dec_zp  	// 0xC6: DEC zp
	.long 0  				// 0xC7: DCP zp			undocumented
	.long opasm_iny     	// 0xC8: INY
	.long opasm_cmp_imm 	// 0xC9: CMP imm
	.long opasm_dex     	// 0xCA: DEX
	.long 0 				// 0xCB: SBX imm		undocumented
	.long opasm_cpy_abs		// 0xCC: CPY abs
	.long opasm_cmp_abs		// 0xCD: CMP abs
	.long opasm_dec_abs		// 0xCE: DEC abs
	.long 0 				// 0xCF: DCP abs		undocumented
	.long opasm_bne     	// 0xD0: BNE
	.long opasm_cmp_indzy	// 0xD1: CMP (),y
	.long opasm_undef
	.long 0					// 0xD3: DCP (),y		undocumented
	.long opasm_nop_zp_x	// 0xD4: NOP zp,x		undocumented
	.long opasm_cmp_zp_x	// 0xD5: CMP zp,x
	.long opasm_dec_zp_x	// 0xD6: DEC zp,x
	.long 0					// 0xD7: DCP zp,x		undocumented
	.long opasm_cld     	// 0xD8: CLD
	.long opasm_cmp_abs_y	// 0xD9: CMP abs,y
	.long opasm_nop     	// 0xDA: NOP			undocumented
	.long 0					// 0xDB: DCP abs,y		undocumented
	.long opasm_nop_abs_x	// 0xDC: NOP abs,x		undocumented
	.long opasm_cmp_abs_x	// 0xDD: CMP abs,x
	.long opasm_dec_abs_x	// 0xDE: DEC abs,x
	.long 0					// 0xDF: DCP abs,x		undocumented
	.long opasm_cpx_imm 	// 0xE0: CPX imm
	.long opasm_sbc_indzx	// 0xE1: SBC (,x)
	.long opasm_nop_imm 	// 0xE2: NOP imm		undocumented
	.long 0					// 0xE3: ISB (,x)		undocumented
	.long opasm_cpx_zp  	// 0xE4: CPX zp
	.long opasm_sbc_zp  	// 0xE5: SBC zp
	.long opasm_inc_zp  	// 0xE6: INC zp
	.long 0  				// 0xE7: ISB zp			undocumented
	.long opasm_inx     	// 0xE8: INX
	.long opasm_sbc_imm 	// 0xE9: SBC imm
	.long opasm_nop     	// 0xEA: NOP
	.long opasm_sbc_imm 	// 0xEB: SBC imm		undocumented
	.long opasm_cpx_abs		// 0xEC: CPX abs
	.long opasm_sbc_abs 	// 0xED: SBC abs
	.long opasm_inc_abs 	// 0xEE: INC abs
	.long 0 				// 0xEF: ISB abs		undocumented
	.long opasm_beq    		// 0xF0: BEQ
	.long opasm_sbc_indzy	// 0xF1: SBC (),y
	.long opasm_undef
	.long 0					// 0xF3: ISB (),y		undocumented
	.long opasm_nop_zp_x 	// 0xF4: NOP zp,x		undocumented
	.long opasm_sbc_zp_x	// 0xF5: SBC zp,x
	.long opasm_inc_zp_x	// 0xF6: INC zp,x
	.long 0					// 0xF7: ISB zp,x		undocumented
	.long opasm_sed     	// 0xF8: SED
	.long opasm_sbc_abs_y	// 0xF9: SBC abs,y
	.long opasm_nop     	// 0xFA: NOP			undocumented
	.long 0					// 0xFB: ISB abs,y		undocumented
	.long opasm_nop_abs_x	// 0xFC: NOP abs,x		undocumented
	.long opasm_sbc_abs_x	// 0xFD: SBC abs,x
	.long opasm_inc_abs_x	// 0xFE: INC abs,x
	.long 0					// 0xFF: ISB abs,x		undocumented

