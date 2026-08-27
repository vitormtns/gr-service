package com.gerenciadorrural.modules.organizations.api; class TenantContextHeaderException extends RuntimeException { final String code; TenantContextHeaderException(String code){this.code=code;} }
