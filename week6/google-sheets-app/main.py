
import gspread
from datetime import datetime

# 1. Authenticate with Google
gc = gspread.service_account(
    filename="credentials.json"
)

# 2. Open your Google Spreadsheet
SPREADSHEET_ID = "1zV6eGhOD9J8vOvXSuQiky4k4Zch06J8BZRhOYXlJS_0"

spreadsheet = gc.open_by_key(SPREADSHEET_ID)

# 3. Select the first worksheet
sheet = spreadsheet.sheet1

# 4. Prepare data
data = [
    "1",
    "Lucia",
    "hello from python",
    datetime.now().strftime("%Y-%m-%d %H:%M:%S")
]

# 5. Append a new row
sheet.append_row(data)

print("Successfully wrote data to Google Sheets!")
