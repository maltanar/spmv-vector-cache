#!/usr/bin/env python

import io, numpy, scipy, struct, os
from scipy import io as ios

dramBase=0x8000100

# source directory for matrices (in Matrix Market format)
localRoot="/home/maltanar/sandbox/spmv-vector-cache/matrices/mtx"
# converted matrices will be outputted here
outputBase="/home/maltanar/sandbox/spmv-vector-cache/matrices"


# load matrix from local file system (Matrix Market format file must exist
# under localRoot)
def loadMatrix(name):
  fileName=localRoot+"/"+name+".mtx"
  if os.path.exists(fileName):
    return ios.mmread(fileName).tocsc()
  else:
    print "Matrix not found! " + fileName

# increment base address by <increment> and ensure alignment to <align>
# alignment is important to burst reads from memory. since the output of this
# script decides where data lives in memory, we have to do this here.
def alignedIncrement(base, increment, align):
  res = base + increment
  rem = res % align
  if rem != 0:
    res += align-rem
  return res

# read in a matrix, convert it to separate CSC SpMV data files + output
# command info (for reading this from an SD card later)
def convertMatrix(name,startAddr=dramBase):
  startingRow=0
  A=loadMatrix(name)
  targetDir=outputBase + "/" + name
  if not os.path.exists(targetDir):
    os.makedirs(targetDir)
    
  burstAlign=64
  commands = []
  
  # metadata file: information about matrix dimensions
  fileName = targetDir + "/" + name + "-meta.bin"  
  metaDataFile = io.open(fileName, "wb")
  metaDataFile.write(struct.pack("I", A.shape[0]))
  metaDataFile.write(struct.pack("I", A.shape[1]))
  metaDataFile.write(struct.pack("I", A.nnz))
  metaDataFile.write(struct.pack("I", startingRow))
  # add command info for metadata
  commands += [(fileName.replace(outputBase, ""), startAddr)]
  # don't close the metadata file yet, we'll write the data pointers
  # to SpMV components
  fileSize = 28 # metadata is always 28 bytes (4 uint32 sizes + 3 pointers)
  startAddr = alignedIncrement(startAddr, fileSize, burstAlign)
  
  # index pointers
  # save indptr data start into metadata
  metaDataFile.write(struct.pack("I", startAddr))
  fileName = targetDir + "/" + name + "-indptr.bin"
  indPtrFile = io.open(fileName, "wb")
  indPtrFile.write(A.indptr)
  indPtrFile.close()
  # add command info
  commands += [(fileName.replace(outputBase, ""), startAddr)]
  
  # increment start address and align
  fileSize = os.path.getsize(fileName)
  startAddr = alignedIncrement(startAddr, fileSize, burstAlign)
    
  # save indices
  # save inds data start into metadata
  metaDataFile.write(struct.pack("I", startAddr))    
  fileName = targetDir + "/" + name + "-inds.bin"
  indsFile = io.open(fileName, "wb")
  indsFile.write(A.indices)
  indsFile.close()
  # create copy command
  commands += [(fileName.replace(outputBase, ""), startAddr)]  
  fileSize = os.path.getsize(fileName)  
  # align for next partition
  startAddr = alignedIncrement(startAddr, fileSize, burstAlign)
  
  # save indices
  # save elem data start into metadata
  metaDataFile.write(struct.pack("I", startAddr))    
  fileName = targetDir + "/" + name + "-data.bin"
  indsFile = io.open(fileName, "wb")
  indsFile.write(A.data)
  indsFile.close()
  # create copy command
  commands += [(fileName.replace(outputBase, ""), startAddr)]  
  fileSize = os.path.getsize(fileName)    
  
  metaDataFile.close()
  
  print "Rows = " + str(A.shape[0])
  print "Cols = " + str(A.shape[1])
  print "NonZ = " + str(A.nnz)
  
  makeUploadScript(commands, targetDir+"/upload.tcl")
  
  return [startAddr, commands]

def makeUploadScript(commands, fileName):
  cmds=map(lambda x: createCopyCommand(outputBase+x[0], x[1]), commands)
  script=reduce(lambda x,y: x+"\n"+y, cmds)
  script="connect arm hw\n"+script+"\ndisconnect 64\n"
  scriptFile = io.open(fileName, "wb")
  scriptFile.write(script)
  scriptFile.close()
  return fileName

def createCopyCommand(fileName, startAddr):
  addrString = ("0x%0.8X" % startAddr)
  return "dow -data " + fileName + " " + addrString

# TODO generate matrix manager code from commands

# ============================================================================

def printCommands():
  for (i,j) in roots.items():
    for ri in j:
      print str(ids[i])+" 4 "+str(ri)+" h"

def makeGraphList():
  graphs = []
  for s in scales:
    for e in efs:
      if os.path.exists(localRoot+rmat(s,e)+".mat"):
        graphs += [rmat(s,e)]
  graphs = ["rmat-19-32"]
  return graphs

def buildGraphManager(c):
  graphs = makeGraphList()
  graphID = 0
  db = []
  code = "#include <iostream>\n"
  code += '#include "sdcard.h"\n'
  code += "void * loadMatrix-p"+str(c)+"(unsigned int id) {\n"  
  code += "switch(id) {\n"
  
  for g in graphs:
    m = GraphMatrix()
    print "Graph " + g + " with " + str(c) + " partitions"      
    m.prepareGraph(g, c)
    db += [(graphID, m.graphName)]
    code += "case " + str(graphID) + ":\n"
    code += m.makeGraphLoadCommand() +"\n"
    code += "break;\n"
    graphID += 1
  code += "default:\n"
  code += 'std::cout << "Matrix not found!" << std::endl; return (void*)0;\n'
  code += "}\n return (void *)" + str(dramBase) + ";\n}\n\n\n"

  code += "const char * getMatrixName-p"+str(c)+"(unsigned int id) {\n"
  code += "switch(id) {\n"
  for ent in db:
    code += "case " + str(ent[0]) + ":\n"
    code += '  return "' + ent[1] + '";\n'
  code += "default:\n"
  code += 'return "Matrix not found!";\n'  
  code += "}}\n"
  
  commandFile= io.open(graphDataRoot + "GraphManager-p"+str(c)+".cc", "wb")
  commandFile.write(code)
  commandFile.close()
  
      
def makeSDReadCommand(fileName, address):
  cmd = "readFromSDCard(<fileName>, <address>);"
  cmd = cmd.replace("<fileName>", '"' + fileName.replace(graphDataRoot, "") + '"')
  cmd = cmd.replace("<address>", str(address))
  return cmd

def prepareBFSResultTest(graphName, rootNode):
  targetDir="/home/maltanar/graph/test"
  resVecAddr=0x1f000000
  res=ssspUnweighted(graphName, rootNode)  
  addrString = ("0x%0.8X" % resVecAddr)
  graphName=graphName.replace("/","-")
  targetDir += "/test-"+graphName+"-root"+str(rootNode)
  if not os.path.exists(targetDir):
    os.makedirs(targetDir)
  testFile = io.open(targetDir+"/golden.bin","wb")
  # our implementation returns -1 for untouched nodes, while scipy returns inf
  # convert inf's to uint32 representations of -1
  def numConvert(i):
    if i == scipy.inf:
      return numpy.uint32(-1)
    else:
      return numpy.uint32(i)
  
  res = numpy.array([numConvert(i) for i in res])
  testFile.write(res)
  testFile.close()
  cmd = "connect arm hw\n"
  cmd += "data_verify " + targetDir+"/golden.bin "+addrString+"\n"
  cmd += "disconnect 64"
  commandFile= io.open(targetDir + "/test.tcl", "wb")
  commandFile.write(cmd)
  commandFile.close()
  print "Test data created. Try:"
  print "xmd -tcl " + targetDir + "/test.tcl"
  

class GraphMatrix:
  def __init__(self):
    self.copyCommandBuffer = []
    self.graphName = ""
    
  def resetCommandBuffer(self):
    self.copyCommandBuffer = []
    
  def makeGraphLoadCommand(self):
    #cmd = "void loadGraph_<graphName>() {\n"
    #cmd = cmd.replace("<graphName>", self.graphName)
    cmd = ""
    for c in self.copyCommandBuffer:
      cmd += "\t" + makeSDReadCommand(c[1], c[0]) + "\n"
    #cmd += "}"
    return cmd
    
  def serializeGraphData(self, graph, name, rootFolder, startAddr, startRow):
    burst = 256*8
    A = loadGraph(graph)
    cmd = ""
    cmdVerify = ""
    # save metadata
    fileName = rootFolder + "/" + name + "-meta.bin"  
    metaDataFile = io.open(fileName, "wb")
    metaDataFile.write(struct.pack("I", A.shape[0]))
    metaDataFile.write(struct.pack("I", A.shape[1]))
    metaDataFile.write(struct.pack("I", A.nnz))
    metaDataFile.write(struct.pack("I", startRow))
    
    # create copy command
    fileSize = 24 # metadata size = 6 fields * 4 bytes
    cmd += self.createCopyCommand(fileName, startAddr)
    cmdVerify += createVerifyCommand(fileName, startAddr)
    
    # increment start address and align
    startAddr = alignedIncrement(startAddr, fileSize, burst)
    # save index pointers
    fileName = rootFolder + "/" + name + "-indptr.bin"
    indPtrFile = io.open(fileName, "wb")
    indPtrFile.write(A.indptr)
    indPtrFile.close()
    # create copy command
    fileSize = os.path.getsize(fileName)
    cmd += self.createCopyCommand(fileName, startAddr)
    cmdVerify += createVerifyCommand(fileName, startAddr)
    # save indptr data start into metadata
    metaDataFile.write(struct.pack("I", startAddr))
    
    # increment start address and align
    startAddr = alignedIncrement(startAddr, fileSize, burst)
      
    # save indices
    fileName = rootFolder + "/" + name + "-inds.bin"
    indsFile = io.open(fileName, "wb")
    indsFile.write(A.indices)
    indsFile.close()
    # create copy command
    fileSize = os.path.getsize(fileName)
    cmd += self.createCopyCommand(fileName, startAddr)
    cmdVerify += createVerifyCommand(fileName, startAddr)
    # save inds data start into metadata
    metaDataFile.write(struct.pack("I", startAddr))
    # align for next partition
    startAddr = alignedIncrement(startAddr, fileSize, burst)
    
    metaDataFile.close()
    
    print "Rows = " + str(A.shape[0])
    print "Cols = " + str(A.shape[1])
    print "NonZ = " + str(A.nnz)
    # return the xmd command and new start address
    return [cmd, startAddr, cmdVerify]  
  
    
  def prepareGraph(self, graphName, partitionCount, csr=False):
    startAddr=dramBase
    # load the graph
    graph = loadGraph(graphName)
    if(csr):
      graphName += "-csr"
    else:
      graphName += "-csc"
      # SpMV BFS needs transpose of matrix
      graph = graph.transpose()
      
    graphName=graphName.replace("/","-")
    # create the graph partitions
    partitions = horizontalSplit(graph, partitionCount)
    # add subfolder with name and part count
    targetDir = graphDataRoot +graphName+"-"+str(partitionCount)
    # create the target dir if it does not exist
    if not os.path.exists(targetDir):
      os.makedirs(targetDir)
    # serialize the graph data and build commands
    i = 0
    cmd = "connect arm hw\n"
    cmdVerify = "connect arm hw\n"
  
    # reserve the first 1024 bytes on startAddr for metadata pointers and 
    # partition count (config.bin)
    partitionBase = startAddr + 1024
    configFile = io.open(targetDir + "/config.bin", "wb")
    # first word of config.bin is the number of partitions
    configFile.write(struct.pack("I", partitionCount))
    
    startRow = 0
    for i in range(0, partitionCount):
      print "Partition " + str(i)
      # write the metadata base ptr into 
      configFile.write(struct.pack("I", partitionBase))
      print "Base: " + ("0x%0.8X" % partitionBase)
      if(csr):
        res=self.serializeGraphData(partitions[i].tocsr(), graphName + "-" + str(i), 
                               targetDir, partitionBase, startRow)
      else:
        res=self.serializeGraphData(partitions[i].tocsc(), graphName + "-" + str(i), 
                               targetDir, partitionBase, startRow)
       # update the xmd commands and next partition base / start row
      cmd += res[0]
      cmdVerify += res[2]
      startRow += partitions[i].shape[0]
      partitionBase = res[1]
      i += 1
    configFile.close()
    print "Upper memory: " + ("0x%0.8X" % partitionBase)
    # add copy command for the config file
    cmd += self.createCopyCommand(targetDir + "/config.bin", startAddr)
    cmdVerify += createVerifyCommand(targetDir + "/config.bin", startAddr)
    cmd += "disconnect 64"
    cmdVerify += "disconnect 64"
    commandFile= io.open(targetDir + "/upload.tcl", "wb")
    commandFile.write(cmd)
    commandFile.close()
    
    commandVerifyFile= io.open(targetDir + "/verify.tcl", "wb")
    commandVerifyFile.write(cmdVerify)
    commandVerifyFile.close()
    
    print "Graph " + graphName + " prepared with " + str(partitionCount) + " partitions"
    if csr:
      print "Matrix stored in row-major format"
    else:
      print "Matrix stored in col-major format"
    print "All data is located in " + targetDir + " -- try:"
    print "xmd -tcl " + targetDir +"/upload.tcl"
    print "xmd -tcl " + targetDir +"/verify.tcl"
    self.graphName = graphName

def loadGraph(matrix):
  if scipy.sparse.isspmatrix_csc(matrix) or scipy.sparse.isspmatrix_csr(matrix):
    # return already loaded matrix
    r= removeSelfEdges(matrix)
    # do not adjust dimensions, return directly
    return r
  elif os.path.exists(localRoot+matrix+".mat"):
    # load matrix from local file system
    r = scipy.io.loadmat(localRoot+matrix+".mat")['A']
  else:
    # load matrix from University of Florida sparse matrix collection
    r=removeSelfEdges(uf.get(matrix)['A'])
  # graph must have rows==cols, clip matrix if needed
  rows = r.shape[0]
  cols = r.shape[1]
  
  if rows != cols:
    dim = min(rows,cols)
    r=r[0:dim, 0:dim]
  return r

# convert index pointer array to index length array (our accelerator expects
# lengths instead of pointers
def indPtrsToLen(matrix):
  A=loadGraph(matrix)
  indsIn=A.indptr
  indsOut=[]
  for i in range (1, indsIn.size):
    indsOut += [indsIn[i]-indsIn[i-1]]
  return numpy.array(indsOut, dtype=numpy.int32)


# Slice matrix along rows to create desired number of partitions
# Keeps number of rows equal, does not look at evenness of NZ distribution
def horizontalSplit(matrix, numPartitions):
  csr = loadGraph(matrix).tocsr()
  submatrices = []
  # align partition size (rowcount) to 64 - good for bursts and 
  # updating the input vector bits independently
  stepSize = alignedIncrement(csr.shape[0] / numPartitions, 0, 64)
  # create submatrices, last one includes the remainder
  currentRow=0
  for i in range(0, numPartitions):
    if i != numPartitions-1:
      submatrices += [csr[currentRow:currentRow+stepSize]]
    else:
      submatrices += [csr[currentRow:]]
    currentRow += stepSize
  return submatrices


def createVerifyCommand(fileName, startAddr):
  addrString = ("0x%0.8X" % startAddr)
  cmd = "data_verify " + fileName + " " + addrString + "\n"
  return cmd


  
def countEmptyCols(graph):
  A=loadGraph(graph).tocsc()
  zeroCount=0
  for i in range(1, 133):
    if A.indptr[i] == A.indptr[i-1]:
      zeroCount+=1
  return zeroCount
  