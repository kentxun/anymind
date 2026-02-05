import Foundation
import SQLite3

private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

enum SQLiteError: Error, CustomStringConvertible {
    case openFailed(String)
    case execFailed(String)
    case prepareFailed(String)
    case stepFailed(String)

    var description: String {
        switch self {
        case .openFailed(let message): return "SQLite open failed: \(message)"
        case .execFailed(let message): return "SQLite exec failed: \(message)"
        case .prepareFailed(let message): return "SQLite prepare failed: \(message)"
        case .stepFailed(let message): return "SQLite step failed: \(message)"
        }
    }
}

final class SQLiteDatabase {
    private let db: OpaquePointer?
    private let queue = DispatchQueue(label: "prompt.sqlite.queue")

    init(path: String) throws {
        var handle: OpaquePointer?
        if sqlite3_open(path, &handle) != SQLITE_OK {
            let message = handle.map { String(cString: sqlite3_errmsg($0)) } ?? "unknown error"
            throw SQLiteError.openFailed(message)
        }
        db = handle
        try execute("PRAGMA foreign_keys = ON;")
        try execute("PRAGMA journal_mode = WAL;")
    }

    deinit {
        if let db {
            sqlite3_close(db)
        }
    }

    func withConnection<T>(_ block: (OpaquePointer?) throws -> T) rethrows -> T {
        try queue.sync {
            try block(db)
        }
    }

    func execute(_ sql: String) throws {
        try withConnection { db in
            guard sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK else {
                throw SQLiteError.execFailed(String(cString: sqlite3_errmsg(db)))
            }
        }
    }

    func prepare(_ sql: String, db: OpaquePointer?) throws -> OpaquePointer? {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else {
            throw SQLiteError.prepareFailed(String(cString: sqlite3_errmsg(db)))
        }
        return statement
    }
}

enum SQLiteBind {
    static func text(_ statement: OpaquePointer?, index: Int32, value: String?) {
        if let value {
            sqlite3_bind_text(statement, index, value, -1, SQLITE_TRANSIENT)
        } else {
            sqlite3_bind_null(statement, index)
        }
    }

    static func int(_ statement: OpaquePointer?, index: Int32, value: Int) {
        sqlite3_bind_int(statement, index, Int32(value))
    }

    static func int64(_ statement: OpaquePointer?, index: Int32, value: Int64) {
        sqlite3_bind_int64(statement, index, value)
    }

    static func bool(_ statement: OpaquePointer?, index: Int32, value: Bool) {
        sqlite3_bind_int(statement, index, value ? 1 : 0)
    }
}

enum SQLiteColumn {
    static func text(_ statement: OpaquePointer?, index: Int32) -> String {
        guard let cString = sqlite3_column_text(statement, index) else { return "" }
        return String(cString: cString)
    }

    static func int(_ statement: OpaquePointer?, index: Int32) -> Int {
        Int(sqlite3_column_int(statement, index))
    }

    static func int64(_ statement: OpaquePointer?, index: Int32) -> Int64 {
        sqlite3_column_int64(statement, index)
    }

    static func bool(_ statement: OpaquePointer?, index: Int32) -> Bool {
        sqlite3_column_int(statement, index) != 0
    }
}
