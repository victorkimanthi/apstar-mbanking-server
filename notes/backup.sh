#!/bin/bash

# Check if mbanking-server.jar exists
if [ ! -f "mbanking-server.jar" ]; then
  echo "Error: mbanking-server.jar not found."
  exit 1
fi

# Get the current date and time
datetime=$(date +"%Y%m%d%H%M%S")

# Compress the file with the date in the filename
7z a -mx9 "mbanking-server.jar.$datetime.7z" mbanking-server.jar

# Move the compressed file to the backups folder
mv "mbanking-server.jar.$datetime.7z" ../backups/

echo "mbanking-server.jar compressed and moved to backups folder with date $datetime."

