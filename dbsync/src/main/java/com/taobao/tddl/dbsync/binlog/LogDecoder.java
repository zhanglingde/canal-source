package com.taobao.tddl.dbsync.binlog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.BitSet;
import java.util.List;

import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.alibaba.otter.canal.parse.driver.mysql.packets.GTIDSet;
import com.taobao.tddl.dbsync.binlog.event.*;
import com.taobao.tddl.dbsync.binlog.event.mariadb.*;

/**
 * Implements a binary-log decoder.
 *
 * <pre>
 * LogDecoder decoder = new LogDecoder();
 * decoder.handle(...);
 * 
 * LogEvent event;
 * do
 * {
 *     event = decoder.decode(buffer, context);
 * 
 *     // process log event.
 * }
 * while (event != null);
 * // no more events in buffer.
 * </pre>
 *
 * @author <a href="mailto:changyuan.lh@taobao.com">Changyuan.lh</a>
 * @version 1.0
 */
public final class LogDecoder {

    protected static final Log logger    = LogFactory.getLog(LogDecoder.class);

    protected final BitSet     handleSet = new BitSet(LogEvent.ENUM_END_EVENT);

    public LogDecoder(){
    }

    public LogDecoder(final int fromIndex, final int toIndex){
        handleSet.set(fromIndex, toIndex);
    }

    public final void handle(final int fromIndex, final int toIndex) {
        handleSet.set(fromIndex, toIndex);
    }

    public final void handle(final int flagIndex) {
        handleSet.set(flagIndex);
    }

    private LogBuffer compressIterateBuffer;

    /**
     * Decoding an event from binary-log buffer.
     *
     * @return <code>UknownLogEvent</code> if event type is unknown or skipped,
     * <code>null</code> if buffer is not including a full event.
     */
    public LogEvent decode(LogBuffer buffer, LogContext context) throws IOException {
        final int limit = buffer.limit();
        if (limit >= FormatDescriptionLogEvent.LOG_EVENT_HEADER_LEN) {
            LogHeader header = new LogHeader(buffer, context.getFormatDescription());

            final int len = header.getEventLen();
            if (limit >= len) {
                LogEvent event;

                /* Checking binary-log's header */
                if (handleSet.get(header.getType())) {
                    buffer.limit(len);
                    try {
                        /* Decoding binary-log to event */
                        event = decode(buffer, header, context);
                    } catch (IOException e) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("Decoding " + LogEvent.getTypeName(header.getType()) + " failed from: "
                                        + context.getLogPosition(), e);
                        }
                        throw e;
                    } finally {
                        buffer.limit(limit); /* Restore limit */
                    }
                } else {
                    /* Ignore unsupported binary-log. */
                    event = new UnknownLogEvent(header);
                }

                if (event != null) {
                    // set logFileName
                    event.getHeader().setLogFileName(context.getLogPosition().getFileName());
                    event.setSemival(buffer.semival);
                }

                /* consume this binary-log. */
                buffer.consume(len);
                return event;
            }
        }

        /* Rewind buffer's position to 0. */
        buffer.rewind();
        return null;
    }

    /**
     * * process compress binlog payload
     * 
     * @param event
     * @param context
     * @return
     * @throws IOException
     */
    public List<LogEvent> processIterateDecode(LogEvent event, LogContext context) throws IOException {
        List<LogEvent> events = Lists.newArrayList();
        if (event.getHeader().getType() == LogEvent.TRANSACTION_PAYLOAD_EVENT) {
            // iterate for compresss payload
            TransactionPayloadLogEvent compressEvent = ((TransactionPayloadLogEvent) event);
            LogBuffer iterateBuffer = null;
            if (compressEvent.isCompressByZstd()) {
                try (ZstdCompressorInputStream in = new ZstdCompressorInputStream(
                    new ByteArrayInputStream(compressEvent.getPayload()))) {
                    byte[] decodeBytes = IOUtils.toByteArray(in);
                    iterateBuffer = new LogBuffer(decodeBytes, 0, decodeBytes.length);
                }
            } else if (compressEvent.isCompressByNone()) {
                iterateBuffer = new LogBuffer(compressEvent.getPayload(), 0, compressEvent.getPayload().length);
            } else {
                throw new IllegalArgumentException("unknow compress type for " + event.getHeader().getLogFileName()
                                                   + ":" + event.getHeader().getLogPos());
            }

            try {
                context.setIterateDecode(true);
                while (iterateBuffer.hasRemaining()) {// iterate
                    LogEvent deEvent = decode(iterateBuffer, context);
                    if (deEvent == null) {
                        break;
                    }

                    // compress event logPos = 0
                    deEvent.getHeader().setLogFileName(event.getHeader().getLogFileName());
                    deEvent.getHeader().setLogPos(event.getHeader().getLogPos());
                    // 需要重置payload每个event的eventLen , ack位点更新依赖logPos - eventLen,
                    // 原因:每个payload都是uncompress的eventLen,无法对应物理binlog的eventLen
                    // 隐患:memory计算空间大小时会出现放大的情况,影响getBatch的数量
                    deEvent.getHeader().setEventLen(event.getHeader().getEventLen());
                    events.add(deEvent);
                }
            } finally {
                context.setIterateDecode(false);
            }
        } else {
            // TODO support mariadb compress binlog
        }
        return events;
    }

    /**
     * Deserialize an event from buffer.
     *
     * @return <code>UknownLogEvent</code> if event type is unknown or skipped.
     */
    public static LogEvent decode(LogBuffer buffer, LogHeader header, LogContext context) throws IOException {
        FormatDescriptionLogEvent descriptionEvent = context.getFormatDescription();
        LogPosition logPosition = context.getLogPosition();

        int checksumAlg = LogEvent.BINLOG_CHECKSUM_ALG_UNDEF;
        if (header.getType() != LogEvent.FORMAT_DESCRIPTION_EVENT) {
            checksumAlg = descriptionEvent.header.getChecksumAlg();
        } else {
            // 如果是format事件自己，也需要处理checksum
            checksumAlg = header.getChecksumAlg();
        }

        if (checksumAlg != LogEvent.BINLOG_CHECKSUM_ALG_OFF && checksumAlg != LogEvent.BINLOG_CHECKSUM_ALG_UNDEF) {
            if (context.isIterateDecode()) {
                // transaction compress payload在主事件已经处理了checksum,遍历解析event忽略checksum处理
            } else {
                // remove checksum bytes
                buffer.limit(header.getEventLen() - LogEvent.BINLOG_CHECKSUM_LEN);
            }
        }
        GTIDSet gtidSet = context.getGtidSet();
        LogEvent gtidLogEvent = context.getGtidLogEvent();
        switch (header.getType()) {
            case LogEvent.QUERY_EVENT: {
                QueryLogEvent event = new QueryLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            case LogEvent.XID_EVENT: {
                XidLogEvent event = new XidLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            case LogEvent.TABLE_MAP_EVENT: {
                TableMapLogEvent mapEvent = new TableMapLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                context.putTable(mapEvent);
                return mapEvent;
            }
            case LogEvent.WRITE_ROWS_EVENT_V1:
            case LogEvent.WRITE_ROWS_EVENT: {
                RowsLogEvent event = new WriteRowsLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                event.fillTable(context);
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            case LogEvent.UPDATE_ROWS_EVENT_V1:
            case LogEvent.UPDATE_ROWS_EVENT: {
                RowsLogEvent event = new UpdateRowsLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                event.fillTable(context);
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            case LogEvent.DELETE_ROWS_EVENT_V1:
            case LogEvent.DELETE_ROWS_EVENT: {
                RowsLogEvent event = new DeleteRowsLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                event.fillTable(context);
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            case LogEvent.ROTATE_EVENT: {
                RotateLogEvent event = new RotateLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition = new LogPosition(event.getFilename(), event.getPosition());
                context.setLogPosition(logPosition);
                return event;
            }
            case LogEvent.LOAD_EVENT:
            case LogEvent.NEW_LOAD_EVENT: {
                LoadLogEvent event = new LoadLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.SLAVE_EVENT: /* can never happen (unused event) */
            {
                if (logger.isWarnEnabled()) {
                    logger.warn("Skipping unsupported SLAVE_EVENT from: " + context.getLogPosition());
                }
                break;
            }
            case LogEvent.CREATE_FILE_EVENT: {
                CreateFileLogEvent event = new CreateFileLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.APPEND_BLOCK_EVENT: {
                AppendBlockLogEvent event = new AppendBlockLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.DELETE_FILE_EVENT: {
                DeleteFileLogEvent event = new DeleteFileLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.EXEC_LOAD_EVENT: {
                ExecuteLoadLogEvent event = new ExecuteLoadLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.START_EVENT_V3: {
                /* This is sent only by MySQL <=4.x */
                StartLogEventV3 event = new StartLogEventV3(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.STOP_EVENT: {
                StopLogEvent event = new StopLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.INTVAR_EVENT: {
                IntvarLogEvent event = new IntvarLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.RAND_EVENT: {
                RandLogEvent event = new RandLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            case LogEvent.USER_VAR_EVENT: {
                UserVarLogEvent event = new UserVarLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            case LogEvent.FORMAT_DESCRIPTION_EVENT: {
                descriptionEvent = new FormatDescriptionLogEvent(header, buffer, descriptionEvent);
                context.setFormatDescription(descriptionEvent);
                return descriptionEvent;
            }
            case LogEvent.PRE_GA_WRITE_ROWS_EVENT: {
                if (logger.isWarnEnabled()) {
                    logger.warn("Skipping unsupported PRE_GA_WRITE_ROWS_EVENT from: " + context.getLogPosition());
                }
                // ev = new Write_rows_log_event_old(buf, event_len,
                // description_event);
                break;
            }
            case LogEvent.PRE_GA_UPDATE_ROWS_EVENT: {
                if (logger.isWarnEnabled()) {
                    logger.warn("Skipping unsupported PRE_GA_UPDATE_ROWS_EVENT from: " + context.getLogPosition());
                }
                // ev = new Update_rows_log_event_old(buf, event_len,
                // description_event);
                break;
            }
            case LogEvent.PRE_GA_DELETE_ROWS_EVENT: {
                if (logger.isWarnEnabled()) {
                    logger.warn("Skipping unsupported PRE_GA_DELETE_ROWS_EVENT from: " + context.getLogPosition());
                }
                // ev = new Delete_rows_log_event_old(buf, event_len,
                // description_event);
                break;
            }
            case LogEvent.BEGIN_LOAD_QUERY_EVENT: {
                BeginLoadQueryLogEvent event = new BeginLoadQueryLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.EXECUTE_LOAD_QUERY_EVENT: {
                ExecuteLoadQueryLogEvent event = new ExecuteLoadQueryLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.INCIDENT_EVENT: {
                IncidentLogEvent event = new IncidentLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.HEARTBEAT_LOG_EVENT: {
                HeartbeatLogEvent event = new HeartbeatLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.IGNORABLE_LOG_EVENT: {
                IgnorableLogEvent event = new IgnorableLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.ROWS_QUERY_LOG_EVENT: {
                RowsQueryLogEvent event = new RowsQueryLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            case LogEvent.PARTIAL_UPDATE_ROWS_EVENT: {
                RowsLogEvent event = new UpdateRowsLogEvent(header, buffer, descriptionEvent, true);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                event.fillTable(context);
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            case LogEvent.GTID_LOG_EVENT:
            case LogEvent.ANONYMOUS_GTID_LOG_EVENT: {
                GtidLogEvent event = new GtidLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                if (gtidSet != null) {
                    gtidSet.update(event.getGtidStr());
                    // update latest gtid
                    header.putGtid(gtidSet, event);
                }
                // update current gtid event to context
                context.setGtidLogEvent(event);
                return event;
            }
            case LogEvent.PREVIOUS_GTIDS_LOG_EVENT: {
                PreviousGtidsLogEvent event = new PreviousGtidsLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.TRANSACTION_CONTEXT_EVENT: {
                TransactionContextLogEvent event = new TransactionContextLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.TRANSACTION_PAYLOAD_EVENT: {
                TransactionPayloadLogEvent event = new TransactionPayloadLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.VIEW_CHANGE_EVENT: {
                ViewChangeEvent event = new ViewChangeEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.XA_PREPARE_LOG_EVENT: {
                XaPrepareLogEvent event = new XaPrepareLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.ANNOTATE_ROWS_EVENT: {
                AnnotateRowsEvent event = new AnnotateRowsEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            case LogEvent.BINLOG_CHECKPOINT_EVENT: {
                BinlogCheckPointLogEvent event = new BinlogCheckPointLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                logPosition.fileName = event.getFilename();
                return event;
            }
            case LogEvent.GTID_EVENT: {
                MariaGtidLogEvent event = new MariaGtidLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                if (gtidSet != null) {
                    gtidSet.update(event.getGtidStr());
                    // update latest gtid
                    header.putGtid(gtidSet, event);
                }
                // update current gtid event to context
                context.setGtidLogEvent(event);
                return event;
            }
            case LogEvent.GTID_LIST_EVENT: {
                MariaGtidListLogEvent event = new MariaGtidListLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                if (gtidSet != null) {
                    gtidSet.update(event.getGtidStr());
                    // update latest gtid
                    header.putGtid(gtidSet, event);
                }
                // update current gtid event to context
                context.setGtidLogEvent(event);
                return event;
            }
            case LogEvent.START_ENCRYPTION_EVENT: {
                StartEncryptionLogEvent event = new StartEncryptionLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.HEARTBEAT_LOG_EVENT_V2: {
                HeartbeatV2LogEvent event = new HeartbeatV2LogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.QUERY_COMPRESSED_EVENT: {
                QueryCompressedLogEvent event = new QueryCompressedLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            }
            case LogEvent.WRITE_ROWS_COMPRESSED_EVENT_V1:
            case LogEvent.WRITE_ROWS_COMPRESSED_EVENT: {
                WriteRowsCompressLogEvent event = new WriteRowsCompressLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                event.fillTable(context);
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            case LogEvent.UPDATE_ROWS_COMPRESSED_EVENT_V1:
            case LogEvent.UPDATE_ROWS_COMPRESSED_EVENT: {
                UpdateRowsCompressLogEvent event = new UpdateRowsCompressLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                event.fillTable(context);
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            case LogEvent.DELETE_ROWS_COMPRESSED_EVENT_V1:
            case LogEvent.DELETE_ROWS_COMPRESSED_EVENT: {
                DeleteRowsCompressLogEvent event = new DeleteRowsCompressLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                event.fillTable(context);
                header.putGtid(context.getGtidSet(), gtidLogEvent);
                return event;
            }
            default:
                /*
                 * Create an object of Ignorable_log_event for unrecognized
                 * sub-class. So that SLAVE SQL THREAD will only update the
                 * position and continue.
                 */
                if ((buffer.getUint16(LogEvent.FLAGS_OFFSET) & LogEvent.LOG_EVENT_IGNORABLE_F) > 0) {
                    IgnorableLogEvent event = new IgnorableLogEvent(header, buffer, descriptionEvent);
                    /* updating position in context */
                    logPosition.position = header.getLogPos();
                    return event;
                } else {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Skipping unrecognized binlog event " + LogEvent.getTypeName(header.getType())
                                    + " from: " + context.getLogPosition());
                    }
                }
        }

        /* updating position in context */
        logPosition.position = header.getLogPos();
        /* Unknown or unsupported log event */
        return new UnknownLogEvent(header);
    }
}
