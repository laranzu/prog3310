
"""
    TCP utility code for ANU COMP3310.
    Read and write lines of text over TCP socket, handling
    EOL and decoding/encoding UTF-8. Nothing very complex
    but avoids copying and pasting over and over again.

    There is no limit on the size of a line.

    Written by H Fisher u9011925, ANU, 2024
    This code may be freely copied and modified
"""

import socket

def writeLine(sock, txt):
    """Write single line with LF"""
    txt += '\n'
    # Use sendall rather than send because if txt is really long,
    # sendall will break into smaller chunks. send() does not.
    sock.sendall(txt.encode('utf-8'))

def readLine(sock):
    """Read single line terminated by \n from sock"""
    # Read as bytes. Only convert to UTF-8 when we have entire line.
    inData = b''
    while True:
        ch = sock.recv(1)
        if len(ch) == 0:
            # Socket closed
            break
        inData += ch
        # This comparison always works with UTF-8 because high bytes
        # of multi byte characters have at least bit 7 set
        if ch == b'\n':
            break
    # Back slash replace won't raise exception on illegal char sequence
    txt = inData.decode('utf-8', 'backslashreplace')
    return txt
