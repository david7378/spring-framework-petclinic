<%@ page session="false" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="petclinic" tagdir="/WEB-INF/tags" %>

<petclinic:layout pageName="reports">
    <h2 id="reports">Reports</h2>
    <p>Nightly reports generated in <code><c:out value="${reportsDir}"/></code></p>

    <c:choose>
        <c:when test="${empty reports}">
            <p>No reports yet.</p>
        </c:when>
        <c:otherwise>
            <table id="reportsTable" class="table table-striped" aria-describedby="reports">
                <thead>
                <tr>
                    <th scope="col">File</th>
                    <th scope="col">Size (bytes)</th>
                    <th scope="col">Generated</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${reports}" var="report">
                    <spring:url value="/reports/{name}" var="reportUrl">
                        <spring:param name="name" value="${report.name}"/>
                    </spring:url>
                    <tr>
                        <td><a href="${fn:escapeXml(reportUrl)}"><c:out value="${report.name}"/></a></td>
                        <td><c:out value="${report.size}"/></td>
                        <td><c:out value="${report.modified}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</petclinic:layout>
