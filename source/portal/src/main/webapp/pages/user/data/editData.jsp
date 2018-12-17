<%@ taglib prefix="s" uri="/struts-tags" %>

<head>
	<title>Data</title>
	<content tag="menu">Home</content>

</head>
<body>
<h2>Edit <i><s:property value="label" /></i></h2>

<s:form id="editDocument" cssClass="form-horizontal" action="editDocument" theme="simple" role="form" method="post">
	<div class="form-horizontal">

	<s:if test="%{hasSourceDocument()}">
		<div id="contents">
			<s:textarea id="cipres-editor" cssClass="form-control editor" name="data" rows="20" wrap="off"
				spellcheck="false" maxlength="%{getMaxSize()}" />
		</div>
	</s:if>

	<div class="form-group form-buttons">
		<div class="col-xs-12">
			<s:submit value="Save and Overwrite" cssClass="btn btn-primary" method="saveAndOverwrite"/>
			<s:submit value="Save as New" cssClass="btn btn-primary" method="saveAsNew"/>
			<s:submit value="Cancel" cssClass="btn btn-primary" method="cancel"/>
		</div>
	</div>

</s:form>

</body>
