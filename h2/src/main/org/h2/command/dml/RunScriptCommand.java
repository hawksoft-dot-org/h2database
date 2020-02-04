/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.command.dml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.h2.command.CommandInterface;
import org.h2.command.Prepared;
import org.h2.engine.Session;
import org.h2.message.DbException;
import org.h2.result.ResultInterface;
import org.h2.util.ScriptReader;

/**
 * This class represents the statement
 * RUNSCRIPT
 */
public class RunScriptCommand extends ScriptBase {

    /**
     * The byte order mark.
     * 0xfeff because this is the Unicode char
     * represented by the UTF-8 byte order mark (EF BB BF).
     */
    private static final char UTF8_BOM = '\uFEFF';

    private Charset charset = StandardCharsets.UTF_8;

    private boolean variableBinary;

    public RunScriptCommand(Session session) {
        super(session);
    }

    @Override
    public int update() {
        session.getUser().checkAdmin();
        int count = 0;
        boolean oldVariableBinary = session.isVariableBinary();
        try {
            openInput();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset));
            // if necessary, strip the BOM from the front of the file
            reader.mark(1);
            if (reader.read() != UTF8_BOM) {
                reader.reset();
            }
            if (variableBinary) {
                session.setVariableBinary(true);
            }
            ScriptReader r = new ScriptReader(reader);
            while (true) {
                String sql = r.readStatement();
                if (sql == null) {
                    break;
                }
                execute(sql);
                count++;
                if ((count & 127) == 0) {
                    checkCanceled();
                }
            }
            r.close();
        } catch (IOException e) {
            throw DbException.convertIOException(e, null);
        } finally {
            if (variableBinary) {
                session.setVariableBinary(oldVariableBinary);
            }
            closeIO();
        }
        return count;
    }

    private void execute(String sql) {
        try {
            Prepared command = session.prepare(sql);
            if (command.isQuery()) {
                command.query(0);
            } else {
                command.update();
            }
            if (session.getAutoCommit()) {
                session.commit(false);
            }
        } catch (DbException e) {
            throw e.addSQL(sql);
        }
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    /**
     * Changes parsing of a BINARY data type.
     *
     * @param variableBinary
     *            {@code true} to parse BINARY as VARBINARY, {@code false} to
     *            parse it as is
     */
    public void setVariableBinary(boolean variableBinary) {
        this.variableBinary = variableBinary;
    }

    @Override
    public ResultInterface queryMeta() {
        return null;
    }

    @Override
    public int getType() {
        return CommandInterface.RUNSCRIPT;
    }

}
