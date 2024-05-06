
"""
    TCP utility code for ANU COMP3310.
    Read and write lines of text over TCP socket, handling
    EOL and decoding/encoding UTF-8. Nothing very complex
    but avoids copying and pasting over and over again.

    There is no limit on the size of a line.

    Written by Hugh Fisher u9011925, ANU, 2024
    Released under Creative Commons CC0 Public Domain Dedication
    This code may be freely copied and modified for any purpose
"""

import socket

def writeLine(sock, txt):
    """Write single line with CR-LF"""
    txt += '\r\n'
    # Use sendall rather than send because if txt is really long,
    # sendall will break into smaller chunks. send() does not.
    sock.sendall(txt.encode('utf-8'))

def readLine(sock):
    """Read single line terminated by \r\n from sock, or None if closed."""
    # Read as bytes. Only convert to UTF-8 when we have entire line.
    inData = b''
    while True:
        ch = sock.recv(1)
        if len(ch) == 0:
            # Socket closed. If we have any data it is an incomplete
            # line, otherwise immediately return None
            if len(inData) > 0:
                break
            else:
                return None
        inData += ch
        # This comparison always works with UTF-8 because high bytes
        # of multi byte characters have at least bit 7 set
        if ch == b'\n':
            break
    if inData.endswith(b'\r\r'):
        inData = inData[0:-2]
    # Back slash replace won't raise exception on illegal char sequence
    txt = inData.decode('utf-8', 'backslashreplace')
    return txt
