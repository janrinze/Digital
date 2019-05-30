/*
 * Copyright (c) 2017 Helmut Neemann
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.core.memory;

import de.neemann.digital.core.*;
import de.neemann.digital.core.element.*;
import de.neemann.digital.lang.Lang;

import java.util.ArrayList;

import static de.neemann.digital.core.element.PinInfo.input;

/**
 * RAM module with different ports to read and write the data
 * and an additional read port. Used to implement graphic card memory.
 */
public class RAMMultiAccess extends Node implements Element, RAMInterface {

    /**
     * The RAMs {@link ElementTypeDescription}
     */
    public static final ElementTypeDescription DESCRIPTION = new ElementTypeDescription(RAMMultiAccess.class) {
        @Override
        public PinDescriptions getInputDescription(ElementAttributes elementAttributes) {
            int writePorts = elementAttributes.get(Keys.WRITE_PORTS);
            int readPorts = elementAttributes.get(Keys.READ_PORTS);
            PinDescription[] names = new PinDescription[writePorts * 3 + readPorts + 1];
            for (int i = 0; i < writePorts; i++) {
                names[i * 3] = input("WE" + i, Lang.get("elem_RAMMultiAccess_pin_WE_N", i));
                names[i * 3 + 1] = input("WA" + i, Lang.get("elem_RAMMultiAccess_pin_WA_N", i));
                names[i * 3 + 2] = input("WD" + i, Lang.get("elem_RAMMultiAccess_pin_WD_N", i));
            }
            for (int i = 0; i < readPorts; i++)
                names[i + writePorts * 3] = input("RA" + i, Lang.get("elem_RAMMultiAccess_pin_RA_N", i));

            names[names.length - 1] = input("C", Lang.get("elem_RAMMultiAccess_pin_C")).setClock();
            return new PinDescriptions(names);
        }

    }
            .addAttribute(Keys.ROTATE)
            .addAttribute(Keys.BITS)
            .addAttribute(Keys.ADDR_BITS)
            .addAttribute(Keys.WRITE_PORTS)
            .addAttribute(Keys.READ_PORTS)
            .addAttribute(Keys.IS_PROGRAM_MEMORY)
            .addAttribute(Keys.LABEL);

    private final DataField memory;
    private final ObservableValue[] out;
    private final int addrBits;
    private final int bits;
    private final String label;
    private final int size;
    private final boolean isProgramMemory;
    private final int writePortNum;
    private final int readPortNum;
    private boolean lastClk = false;
    private ArrayList<WritePort> writePorts;
    private ArrayList<ReadPort> readPorts;
    private ObservableValue clkIn;

    /**
     * Creates a new instance
     *
     * @param attr the elemets attributes
     */
    public RAMMultiAccess(ElementAttributes attr) {
        super(true);
        bits = attr.get(Keys.BITS);

        writePortNum = attr.get(Keys.WRITE_PORTS);
        readPortNum = attr.get(Keys.READ_PORTS);

        out = new ObservableValue[readPortNum];
        for (int i = 0; i < readPortNum; i++)
            out[i] = new ObservableValue("D" + i, bits).setDescription(Lang.get("elem_RAMMultiAccess_pin_D_N", i));

        addrBits = attr.get(Keys.ADDR_BITS);
        size = 1 << addrBits;
        memory = new DataField(size);
        label = attr.getLabel();
        isProgramMemory = attr.get(Keys.IS_PROGRAM_MEMORY);
    }

    @Override
    public void setInputs(ObservableValues inputs) throws NodeException {
        writePorts = new ArrayList<>(writePortNum);
        for (int i = 0; i < writePortNum; i++)
            writePorts.add(new WritePort(inputs.get(i * 3), inputs.get(i * 3 + 1), inputs.get(i * 3 + 2)));

        readPorts = new ArrayList<>(readPortNum);
        for (int i = 0; i < readPortNum; i++)
            readPorts.add(new ReadPort(inputs.get(writePortNum * 3 + i), out[i]));

        clkIn = inputs.get(inputs.size() - 1).checkBits(1, this).addObserverToValue(this);
    }

    @Override
    public ObservableValues getOutputs() {
        return new ObservableValues(out);
    }

    @Override
    public void readInputs() throws NodeException {
        boolean clk = clkIn.getBool();
        if (clk && !lastClk)
            for (WritePort rp : writePorts)
                rp.readInputs();

        lastClk = clk;
        for (ReadPort rp : readPorts)
            rp.readInput();
    }

    @Override
    public void writeOutputs() throws NodeException {
        for (ReadPort rp : readPorts)
            rp.writeOutput();
    }

    @Override
    public DataField getMemory() {
        return memory;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public int getDataBits() {
        return bits;
    }

    @Override
    public int getAddrBits() {
        return addrBits;
    }

    @Override
    public boolean isProgramMemory() {
        return isProgramMemory;
    }

    @Override
    public void setProgramMemory(DataField dataField) {
        memory.setDataFrom(dataField);
    }

    private class WritePort {
        private final ObservableValue en;
        private final ObservableValue a;
        private final ObservableValue d;

        private WritePort(ObservableValue en, ObservableValue a, ObservableValue d) throws BitsException {
            this.en = en.checkBits(1, RAMMultiAccess.this);
            this.a = a.checkBits(addrBits, RAMMultiAccess.this);
            this.d = d.checkBits(bits, RAMMultiAccess.this);
        }

        private void readInputs() {
            if (en.getBool()) {
                int addr = (int) a.getValue();
                long data = d.getValue();
                memory.setData(addr, data);
            }
        }
    }

    private class ReadPort {
        private final ObservableValue a;
        private final ObservableValue d;
        private int addr;

        private ReadPort(ObservableValue a, ObservableValue d) throws BitsException {
            this.a = a.checkBits(addrBits, RAMMultiAccess.this).addObserverToValue(RAMMultiAccess.this);
            this.d = d.checkBits(bits, RAMMultiAccess.this);
        }

        private void readInput() {
            addr = (int) a.getValue();
        }

        private void writeOutput() {
            d.setValue(memory.getDataWord(addr));
        }
    }
}
