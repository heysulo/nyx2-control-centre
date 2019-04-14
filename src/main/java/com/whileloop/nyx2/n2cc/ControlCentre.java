/*
 * The MIT License
 *
 * Copyright 2019 Team whileLOOP.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.whileloop.nyx2.n2cc;

import com.whileloop.nyx2.utils.NX2Logger;
import java.sql.SQLException;

public class ControlCentre extends NX2Logger {

    enum ExitCodes {

        DB_DRIVER_FAIL(0x1),
        DB_CONN_FAIL(0x2);

        public int code;

        private ExitCodes(int code) {
            this.code = code;
        }
    }

    public static void main(String[] args) {
        ControlCentre cc = new ControlCentre();

    }

    public ControlCentre() {
        info("Starting NYX2 Conrtrol Centre");
        connectToDatabase();
        AgentService.getInstance();
    }

    private void connectToDatabase() {
        try {
            DBConnection dbc = new DBConnection();
        } catch (ClassNotFoundException ex) {
            crit("Failed to load JDBC driver: %s", ex.getMessage());
            System.exit(ExitCodes.DB_DRIVER_FAIL.code);
        } catch (SQLException ex) {
            crit("Failed to establish database connection: %s", ex.getMessage());
            System.exit(ExitCodes.DB_CONN_FAIL.code);
        }
    }

}
