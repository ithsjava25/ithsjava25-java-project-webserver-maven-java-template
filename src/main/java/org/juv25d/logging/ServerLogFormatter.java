package org.juv25d.logging;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class ServerLogFormatter extends Formatter {
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String format(LogRecord record) {
        String connectionId = LogContext.getConnectionId();
        String idPart = (connectionId != null) ? " [" + connectionId + "]" : "";

        return String.format("%s %s%s: %s%n",
                ZonedDateTime.now(ZoneId.systemDefault()).format(dtf),
                record.getLevel(),
                idPart,
                formatMessage(record));
    }
}
