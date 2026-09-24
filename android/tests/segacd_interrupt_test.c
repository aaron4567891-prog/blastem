/* Run with the Android NDK; see run_segacd_interrupt_test.ps1. */
#include <assert.h>
#include "../../segacd.c"

int main(void)
{
	segacd_context cd = {0};
	m68k_context cpu = {0};
	m68k_options opts = {0};
	opts.gen.clock_divider = 4;
	cpu.opts = &opts;
	cpu.system = &cd;
	cd.m68k = &cpu;
	cpu.status = 0x21;
	cpu.cycles = 1200;
	cpu.sync_cycle = 2000;
	cpu.int_pending = INT_PENDING_NONE;
	cd.gate_array[GA_INT_MASK] = BIT_MASK_IEN5;
	cd.cdc.cycle = 1000;
	cd.cdc.clock_step = 4;
	cd.cdc.ifctrl = 0x20; /* decoder interrupt enabled, IFSTAT asserted */
	cd.cdc.regs[1] = 0xDF;
	cd.cdc.decode_end = CYCLE_NEVER;
	cd.cdc.transfer_end = CYCLE_NEVER;

	calculate_target_cycle(&cpu);
	assert(cpu.int_cycle == cpu.cycles);
	assert(cpu.int_priority == 5);

	/* An ISR may lower its mask before clearing the CDC interrupt line. */
	cd.cdc_int_ack = 1;
	calculate_target_cycle(&cpu);
	assert(cd.cdc_int_ack == 1);
	assert(cpu.int_cycle == CYCLE_NEVER);
	cpu.cycles += 100;
	calculate_target_cycle(&cpu);
	assert(cd.cdc_int_ack == 1);
	assert(cpu.int_cycle == CYCLE_NEVER);

	/* Releasing the line rearms the detector for a later falling edge. */
	cd.cdc.regs[1] = 0xFF;
	calculate_target_cycle(&cpu);
	assert(cd.cdc_int_ack == 0);
	assert(cpu.int_cycle == CYCLE_NEVER);
	cd.cdc.regs[1] = 0xDF;
	calculate_target_cycle(&cpu);
	assert(cpu.int_cycle == cpu.cycles);
	assert(cpu.int_priority == 5);

	/* The same acknowledged level must also stay suppressed at equal clocks. */
	cd.cdc_int_ack = 1;
	cd.cdc.cycle = cpu.cycles;
	calculate_target_cycle(&cpu);
	assert(cd.cdc_int_ack == 1);
	assert(cpu.int_cycle == CYCLE_NEVER);
	puts("Sega CD interrupt regression tests passed");
	return 0;
}
