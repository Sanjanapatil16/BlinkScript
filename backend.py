import os
import sqlite3
import datetime
from flask import Flask, request, jsonify, render_template_string, render_template

app = Flask(__name__)

# State to store the latest blink event
latest_blink = {"count": 0, "id": 0}

# Configurable MySQL Settings (Change these as needed)
MYSQL_HOST = "localhost"
MYSQL_USER = "root"
MYSQL_PASSWORD = ""
MYSQL_DATABASE = "blink_communication"

USE_MYSQL = False
mysql_conn = None

# Attempt to import and setup MySQL
try:
    import mysql.connector
    mysql_conn = mysql.connector.connect(
        host=MYSQL_HOST,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD
    )
    cursor = mysql_conn.cursor()
    cursor.execute(f"CREATE DATABASE IF NOT EXISTS {MYSQL_DATABASE}")
    mysql_conn.close()
    
    # Reconnect to the database
    mysql_conn = mysql.connector.connect(
        host=MYSQL_HOST,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=MYSQL_DATABASE
    )
    USE_MYSQL = True
    print("[INFO] Connected to MySQL database successfully.")
except Exception as e:
    print(f"[WARNING] Could not connect to MySQL: {e}")
    print("[INFO] Falling back to SQLite database: 'blink_database.db'")

SQLITE_DB = "blink_database.db"

def get_db_connection():
    if USE_MYSQL:
        try:
            conn = mysql.connector.connect(
                host=MYSQL_HOST,
                user=MYSQL_USER,
                password=MYSQL_PASSWORD,
                database=MYSQL_DATABASE
            )
            return conn, True
        except Exception as e:
            print(f"[ERROR] MySQL Connection lost during request: {e}. Falling back to SQLite.")
            
    conn = sqlite3.connect(SQLITE_DB)
    # Return SQLite cursor rows as dicts
    conn.row_factory = sqlite3.Row
    return conn, False

# Initialize database tables
def init_db():
    conn, is_mysql = get_db_connection()
    cursor = conn.cursor()
    
    if is_mysql:
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS messages (
                id INT AUTO_INCREMENT PRIMARY KEY,
                sender VARCHAR(50) NOT NULL,
                content TEXT NOT NULL,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS sos_logs (
                id INT AUTO_INCREMENT PRIMARY KEY,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                status VARCHAR(50) NOT NULL
            )
        """)
    else:
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS sos_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                status TEXT NOT NULL
            )
        """)
    conn.commit()
    conn.close()

init_db()

@app.route('/api/message', methods=['POST'])
def log_message():
    data = request.json or {}
    sender = data.get('sender', 'UNKNOWN')
    content = data.get('content', '')
    
    if not content:
        return jsonify({"status": "error", "message": "Content cannot be empty"}), 400
        
    conn, is_mysql = get_db_connection()
    cursor = conn.cursor()
    
    if is_mysql:
        cursor.execute(
            "INSERT INTO messages (sender, content) VALUES (%s, %s)",
            (sender, content)
        )
    else:
        cursor.execute(
            "INSERT INTO messages (sender, content) VALUES (?, ?)",
            (sender, content)
        )
    conn.commit()
    conn.close()
    
    print(f"[LOG] Saved message from {sender}: '{content}'")
    return jsonify({"status": "success", "message": "Message logged successfully"})

@app.route('/api/sos', methods=['POST'])
def log_sos():
    data = request.json or {}
    status = data.get('status', 'PENDING')
    
    conn, is_mysql = get_db_connection()
    cursor = conn.cursor()
    
    if is_mysql:
        cursor.execute(
            "INSERT INTO sos_logs (status) VALUES (%s)",
            (status,)
        )
    else:
        cursor.execute(
            "INSERT INTO sos_logs (status) VALUES (?)",
            (status,)
        )
    conn.commit()
    conn.close()
    
    print("[SOS ALERT] Saved SOS event to database.")
    return jsonify({"status": "success", "message": "SOS alert logged successfully"})

@app.route('/api/history', methods=['GET'])
def get_history():
    conn, is_mysql = get_db_connection()
    cursor = conn.cursor()
    
    if is_mysql:
        cursor.execute("SELECT id, sender, content, timestamp FROM messages ORDER BY id DESC LIMIT 50")
        messages = [{"id": r[0], "sender": r[1], "content": r[2], "timestamp": r[3].strftime('%Y-%m-%d %H:%M:%S') if r[3] else ""} for r in cursor.fetchall()]
        
        cursor.execute("SELECT id, timestamp, status FROM sos_logs ORDER BY id DESC LIMIT 20")
        sos_logs = [{"id": r[0], "timestamp": r[1].strftime('%Y-%m-%d %H:%M:%S') if r[1] else "", "status": r[2]} for r in cursor.fetchall()]
    else:
        cursor.execute("SELECT id, sender, content, timestamp FROM messages ORDER BY id DESC LIMIT 50")
        messages = [dict(r) for r in cursor.fetchall()]
        
        cursor.execute("SELECT id, timestamp, status FROM sos_logs ORDER BY id DESC LIMIT 20")
        sos_logs = [dict(r) for r in cursor.fetchall()]
        
    conn.close()
    return jsonify({
        "messages": messages,
        "sos_logs": sos_logs,
        "database_type": "MySQL" if is_mysql else "SQLite"
    })

# Caregiver Dashboard HTML Template
DASHBOARD_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Caregiver Monitoring Dashboard</title>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #0B0F19;
            --card-bg: #151F32;
            --primary: #3B82F6;
            --accent-green: #10B981;
            --accent-red: #EF4444;
            --text-main: #F3F4F6;
            --text-muted: #9CA3AF;
            --border-color: #1E293B;
        }

        body {
            font-family: 'Plus Jakarta Sans', sans-serif;
            background-color: var(--bg-color);
            color: var(--text-main);
            margin: 0;
            padding: 24px;
            display: flex;
            flex-direction: column;
            align-items: center;
            min-height: 100vh;
        }

        header {
            width: 100%;
            max-width: 1200px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding-bottom: 20px;
            border-bottom: 1px solid var(--border-color);
            margin-bottom: 30px;
        }

        h1 {
            font-size: 28px;
            font-weight: 700;
            margin: 0;
            background: linear-gradient(135deg, #60A5FA, #3B82F6);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }

        .db-badge {
            background-color: rgba(59, 130, 246, 0.15);
            color: #60A5FA;
            padding: 6px 12px;
            border-radius: 20px;
            font-size: 13px;
            font-weight: 600;
            border: 1px solid rgba(59, 130, 246, 0.3);
        }

        .container {
            width: 100%;
            max-width: 1200px;
            display: grid;
            grid-template-columns: 2fr 1fr;
            gap: 24px;
        }

        .card {
            background-color: var(--card-bg);
            border-radius: 16px;
            border: 1px solid var(--border-color);
            padding: 24px;
            box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3);
            display: flex;
            flex-direction: column;
            height: 600px;
        }

        .card-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
        }

        .card-title {
            font-size: 20px;
            font-weight: 600;
            margin: 0;
        }

        .log-list {
            flex-grow: 1;
            overflow-y: auto;
            display: flex;
            flex-direction: column;
            gap: 12px;
            padding-right: 8px;
        }

        /* Scrollbar styling */
        .log-list::-webkit-scrollbar {
            width: 6px;
        }
        .log-list::-webkit-scrollbar-track {
            background: rgba(0, 0, 0, 0.1);
        }
        .log-list::-webkit-scrollbar-thumb {
            background: var(--border-color);
            border-radius: 4px;
        }

        .msg-item {
            background-color: rgba(30, 41, 59, 0.4);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 14px 18px;
            display: flex;
            flex-direction: column;
            gap: 6px;
            animation: fadeIn 0.3s ease-out;
        }

        .msg-item.chatbot {
            border-left: 4px solid var(--primary);
        }

        .msg-item.user {
            border-left: 4px solid var(--accent-green);
        }

        .msg-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .sender-tag {
            font-size: 12px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }

        .sender-tag.chatbot {
            color: #60A5FA;
        }

        .sender-tag.user {
            color: #34D399;
        }

        .timestamp {
            font-size: 11px;
            color: var(--text-muted);
        }

        .msg-content {
            font-size: 15px;
            line-height: 1.5;
            word-break: break-word;
        }

        .sos-item {
            background-color: rgba(239, 68, 68, 0.08);
            border: 1px solid rgba(239, 68, 68, 0.25);
            border-radius: 12px;
            padding: 14px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            animation: pulseAlert 2s infinite ease-in-out;
        }

        .sos-info {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }

        .sos-title {
            color: #F87171;
            font-weight: 700;
            font-size: 14px;
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }

        .sos-btn {
            background-color: var(--accent-red);
            border: none;
            color: white;
            padding: 6px 12px;
            border-radius: 8px;
            font-weight: 600;
            font-size: 12px;
            cursor: pointer;
            transition: all 0.2s;
        }

        .sos-btn:hover {
            background-color: #DC2626;
            transform: scale(1.05);
        }

        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(10px); }
            to { opacity: 1; transform: translateY(0); }
        }

        @keyframes pulseAlert {
            0% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); }
            70% { box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
            100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
        }

        @media (max-width: 900px) {
            .container {
                grid-template-columns: 1fr;
            }
            .card {
                height: 450px;
            }
        }
    </style>
</head>
<body>

    <header>
        <div>
            <h1>BlinkScript Dashboard</h1>
            <p style="color: var(--text-muted); margin: 4px 0 0 0; font-size: 14px;">Caregiver Monitoring Portal</p>
        </div>
        <div id="db-badge" class="db-badge">Database: Connecting...</div>
    </header>

    <div class="container">
        <!-- Message Logs Card -->
        <div class="card">
            <div class="card-header">
                <h2 class="card-title">Live Chat & Interaction Logs</h2>
                <span style="font-size: 12px; color: var(--text-muted);">Updates in real-time</span>
            </div>
            <div class="log-list" id="message-list">
                <!-- Loaded dynamically -->
            </div>
        </div>

        <!-- SOS Alerts Card -->
        <div class="card" style="border-color: rgba(239, 68, 68, 0.2);">
            <div class="card-header">
                <h2 class="card-title" style="color: #F87171;">SOS Emergency Alerts</h2>
            </div>
            <div class="log-list" id="sos-list">
                <!-- Loaded dynamically -->
            </div>
        </div>
    </div>

    <script>
        let lastMessageId = 0;
        let lastSosId = 0;

        function fetchLogs() {
            fetch('/api/history')
                .then(response => response.json())
                .then(data => {
                    // Update Database Badge
                    document.getElementById('db-badge').innerText = "Database: " + data.database_type;
                    
                    // Render Messages
                    const msgList = document.getElementById('message-list');
                    msgList.innerHTML = '';
                    
                    if (data.messages.length === 0) {
                        msgList.innerHTML = '<div style="color: var(--text-muted); text-align: center; margin-top: 100px;">No interaction logs found yet.</div>';
                    } else {
                        data.messages.forEach(msg => {
                            const isUser = msg.sender.toUpperCase() === 'USER';
                            const item = document.createElement('div');
                            item.className = 'msg-item ' + (isUser ? 'user' : 'chatbot');
                            item.innerHTML = `
                                <div class="msg-header">
                                    <span class="sender-tag ${isUser ? 'user' : 'chatbot'}">${msg.sender}</span>
                                    <span class="timestamp">${msg.timestamp}</span>
                                </div>
                                <div class="msg-content">${msg.content}</div>
                            `;
                            msgList.appendChild(item);
                        });
                    }

                    // Render SOS logs
                    const sosList = document.getElementById('sos-list');
                    sosList.innerHTML = '';
                    
                    if (data.sos_logs.length === 0) {
                        sosList.innerHTML = '<div style="color: var(--text-muted); text-align: center; margin-top: 100px;">No emergency alerts.</div>';
                    } else {
                        data.sos_logs.forEach(log => {
                            const item = document.createElement('div');
                            item.className = 'sos-item';
                            item.innerHTML = `
                                <div class="sos-info">
                                    <span class="sos-title">CRITICAL ASSISTANCE</span>
                                    <span class="timestamp">${log.timestamp}</span>
                                </div>
                                <div>
                                    <button class="sos-btn" onclick="alert('Acknowledging Alert!')">Acknowledge</button>
                                </div>
                            `;
                            sosList.appendChild(item);
                        });
                    }
                })
                .catch(err => console.error("Error fetching logs:", err));
        }

        // Poll for updates every 1.5 seconds
        fetchLogs();
        setInterval(fetchLogs, 1500);
    </script>
</body>
</html>
"""

@app.route('/')
def index():
    return render_template_string(DASHBOARD_HTML)

@app.route('/api/blink', methods=['POST'])
def receive_blink():
    global latest_blink
    data = request.json or {}
    count = data.get('count', 0)
    latest_blink = {"count": count, "id": latest_blink["id"] + 1}
    print(f"[BLINK EVENT] Count: {count}, Event ID: {latest_blink['id']}")
    return jsonify({"status": "success", "latest_blink": latest_blink})

@app.route('/api/latest-blink', methods=['GET'])
def get_latest_blink():
    return jsonify(latest_blink)

@app.route('/assistant')
def assistant():
    return render_template('assistant.html')

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
