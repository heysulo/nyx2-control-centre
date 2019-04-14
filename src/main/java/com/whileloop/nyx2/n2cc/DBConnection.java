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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 *
 * @author sulochana
 */
public final class DBConnection extends NX2Logger {

    private static DBConnection instance;

    private Connection connection = null;

    public DBConnection() throws ClassNotFoundException, SQLException {
        debug("Loading JDBC driver");
        this.setVerboseLevel(Loglevel.DEBUG);
        Class.forName("com.mysql.jdbc.Driver");
        String connectionStr;
        connectionStr = String.format("jdbc:mysql://%s/nyx2?autoReconnect=true&useSSL=false&user=%s&password=%s",
                System.getenv("AWS_RDB_HOST"),
                System.getenv("AWS_RDB_USERNAME"), System.getenv("AWS_RDB_PASSWORD"));
        debug("Establishing database connection: %s", connectionStr);
        connection = DriverManager.getConnection(connectionStr);
        info("Connected to database");
        instance = this;
    }

    public static Connection getConnection() {
        return instance.connection;
    }

}
