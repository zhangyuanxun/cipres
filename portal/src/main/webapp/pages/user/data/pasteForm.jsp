<%@ taglib prefix="s" uri="/struts-tags" %>
<head>
  <title>Upload Data</title>
  <content tag="menu">Home</content>
</head>
<body>
<h2>Upload File</h2>
<s:form action="pasteData" theme="simple" method="POST" enctype="multipart/form-data" cssClass="form-horizontal" role="form">
  <div class="form-group">
    <label class="col-xs-2 control-label">Upload your files</label>
    <div class="col-xs-10">
      <s:file name="upload" multiple="multiple"/>
      <br>
      You can select multiple files.<br><br>
      MSIE 9 and below support single uploads only.
    </div>
  </div>
  <hr class="hr-bluedots">
  <b>You can also enter your data manually below</b><p>
  <div class="form-group">
    <label class="col-xs-2 control-label">Label (required)</label>
    <div class="col-xs-10">
      <s:textfield cssClass="form-control" name="sourceLabel" placeholder="Label"/>
    </div>
  </div>
  <div class="form-group">
    <label class="col-xs-2 control-label">Data:</label>
    <div class="col-xs-10">
      <s:textarea cssClass="form-control" name="source" placeholder="Enter your data"/>
    </div>
  </div>
  <div class="form-group">
    <div class="col-xs-offset-2 col-xs-10">
      <s:submit value="Save" cssClass="btn btn-success" method="executePaste"/>
      <s:submit value="Cancel" cssClass="btn btn-primary" method="cancel"/>
    </div>
  </div>
</s:form>

</body>
